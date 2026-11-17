package com.sancan.meal.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.order.service.OrderLockService;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import com.sancan.meal.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 交易域验收测试（手册 §16「必须通过的业务测试」与「支付与账本专项测试」）：
 * 锁单、幂等、并发、余额不足、取消退券、按来源退款、过敏拦截。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    /** 每个用例独占一个服务周，避免锁单任务把其他用例遗留的订单一起处理。 */
    private static final AtomicInteger NEXT_WEEK = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private WalletService walletService;
    @Autowired
    private OrderLockService orderLockService;
    @Autowired
    private com.sancan.meal.plan.dal.mapper.PlanItemMapper planItemMapper;

    private String adminToken;
    private String memberToken;
    private long memberId;
    private LocalDate serviceDate;
    private long peanutVersionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        // 服务日固定在未来，避免依赖运行日期
        serviceDate = LocalDate.now().plusWeeks(2 + NEXT_WEEK.getAndIncrement()).with(DayOfWeek.WEDNESDAY);
        LocalDate weekStart = serviceDate.with(DayOfWeek.MONDAY);
        preparePublishedMenu(weekStart);

        memberToken = memberToken("order-user-" + SEQ.incrementAndGet());
        memberId = me();
        onboard(memberToken);
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
    }

    @Test
    @DisplayName("锁单：扣款、快照、两条配送记录同时生成")
    void lockDeductsAndCreatesSnapshot() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        JsonNode order = createOrder(null);
        long orderId = order.path("id").asLong();
        BigDecimal original = new BigDecimal(order.path("originalAmount").asText());

        JsonNode run = data(admin(post("/admin/meal/orders/lock-run"),
                Map.of("serviceDate", serviceDate.toString())));
        assertThat(run.path("locked").asInt()).isEqualTo(1);

        JsonNode detail = data(admin(get("/admin/meal/orders/" + orderId)));
        assertThat(detail.path("status").asText()).isEqualTo("LOCKED");
        assertThat(detail.path("hasSnapshot").asBoolean()).isTrue();
        assertThat(detail.path("deliveries")).hasSize(3);
        assertThat(new BigDecimal(detail.path("creditAmount").asText())).isEqualByComparingTo(original);

        JsonNode wallet = data(member(get("/app/wallet")));
        assertThat(new BigDecimal(wallet.path("balance").asText()))
                .isEqualByComparingTo(new BigDecimal("500.00").subtract(original));

        // 账本留下 ORDER_PAY 流水
        List<WalletLedgerDO> ledgers = walletService.ledger(memberId, 1, 20);
        assertThat(ledgers).anyMatch(l -> "ORDER_PAY".equals(l.getBizType()));
    }

    @Test
    @DisplayName("锁单任务可重复执行，同一订单只成功锁定一次")
    void lockRunIsIdempotent() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        createOrder(null);
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));

        JsonNode second = data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));
        assertThat(second.path("total").asInt()).isZero();
        assertThat(second.path("locked").asInt()).isZero();

        long payCount = walletService.ledger(memberId, 1, 50).stream()
                .filter(l -> "ORDER_PAY".equals(l.getBizType())).count();
        assertThat(payCount).isEqualTo(1);
    }

    @Test
    @DisplayName("并发锁单：两个线程同时处理同一订单，只有一次扣款")
    void concurrentLockDeductsOnce() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        long orderId = createOrder(null).path("id").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OrderLockService.Outcome>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return orderLockService.lockOne(orderId);
            }));
        }
        start.countDown();
        List<OrderLockService.Outcome> outcomes = new ArrayList<>();
        for (Future<OrderLockService.Outcome> future : futures) {
            outcomes.add(future.get());
        }
        pool.shutdown();

        assertThat(outcomes).contains(OrderLockService.Outcome.LOCKED);
        long locked = outcomes.stream().filter(o -> o == OrderLockService.Outcome.LOCKED).count();
        assertThat(locked).isEqualTo(1);

        long payCount = walletService.ledger(memberId, 1, 50).stream()
                .filter(l -> "ORDER_PAY".equals(l.getBizType())).count();
        assertThat(payCount).isEqualTo(1);
    }

    @Test
    @DisplayName("余额不足：订单不锁定，记录可检索的失败原因")
    void insufficientBalanceKeepsPendingLock() throws Exception {
        grantCredit(new BigDecimal("1.00"));
        long orderId = createOrder(null).path("id").asLong();

        JsonNode run = data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));
        assertThat(run.path("failed").asInt()).isEqualTo(1);

        JsonNode detail = data(admin(get("/admin/meal/orders/" + orderId)));
        assertThat(detail.path("status").asText()).isEqualTo("PENDING_LOCK");
        assertThat(detail.path("lockFailReason").asText()).contains("余额不足");
        assertThat(new BigDecimal(data(admin(get("/admin/meal/wallet/" + memberId)))
                .path("balance").asText())).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("锁单前取消：优惠券退回，账户不动")
    void cancelReleasesCoupon() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        long templateId = createCouponTemplate();
        long couponId = data(admin(post("/admin/meal/coupons/grant"),
                Map.of("userId", memberId, "templateId", templateId))).path("id").asLong();

        long orderId = createOrder(couponId).path("id").asLong();
        data(member(post("/app/orders/" + orderId + "/cancel"), Map.of("reason", "临时有事")));

        JsonNode detail = data(admin(get("/admin/meal/orders/" + orderId)));
        assertThat(detail.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(new BigDecimal(data(admin(get("/admin/meal/wallet/" + memberId)))
                .path("balance").asText())).isEqualByComparingTo("500.00");

        JsonNode coupons = data(member(get("/app/coupons").param("status", "UNUSED")));
        assertThat(coupons).hasSize(1);
        assertThat(coupons.get(0).path("status").asText()).isEqualTo("UNUSED");
    }

    @Test
    @DisplayName("使用优惠券锁单后按折后金额扣款")
    void couponReducesPayable() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        long templateId = createCouponTemplate();
        long couponId = data(admin(post("/admin/meal/coupons/grant"),
                Map.of("userId", memberId, "templateId", templateId))).path("id").asLong();

        JsonNode order = createOrder(couponId);
        BigDecimal original = new BigDecimal(order.path("originalAmount").asText());
        assertThat(new BigDecimal(order.path("couponAmount").asText())).isEqualByComparingTo("10.00");

        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));
        JsonNode detail = data(admin(get("/admin/meal/orders/" + order.path("id").asLong())));
        assertThat(detail.path("status").asText()).isEqualTo("LOCKED");
        assertThat(new BigDecimal(detail.path("balanceAmount").asText()))
                .isEqualByComparingTo(original.subtract(new BigDecimal("10.00")));

        JsonNode coupons = data(admin(get("/admin/meal/coupons").param("userId", String.valueOf(memberId))));
        assertThat(coupons.get(0).path("status").asText()).isEqualTo("USED");
    }

    @Test
    @DisplayName("退款按原来源退回，赠送 credit 不会变成现金")
    void refundRestoresBySource() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        long orderId = createOrder(null).path("id").asLong();
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));

        JsonNode afterLock = data(admin(get("/admin/meal/wallet/" + memberId)));
        assertThat(new BigDecimal(afterLock.path("creditBalance").asText())).isLessThan(new BigDecimal("500.00"));

        data(admin(post("/admin/meal/orders/" + orderId + "/refund"), Map.of("reason", "运营取消")));

        JsonNode afterRefund = data(admin(get("/admin/meal/wallet/" + memberId)));
        assertThat(new BigDecimal(afterRefund.path("balance").asText())).isEqualByComparingTo("500.00");
        assertThat(new BigDecimal(afterRefund.path("creditBalance").asText())).isEqualByComparingTo("500.00");
        assertThat(new BigDecimal(afterRefund.path("cashBalance").asText())).isEqualByComparingTo("0.00");
        assertThat(data(admin(get("/admin/meal/orders/" + orderId))).path("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("含过敏原的餐次阻止下单")
    void allergenBlocksOrder() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        // 换菜接口自身会拦截过敏原，这里直接改库构造冲突场景，验证下单环节的兜底拦截
        com.sancan.meal.plan.dal.dataobject.PlanItemDO lunch = planItemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sancan.meal.plan.dal.dataobject.PlanItemDO>()
                        .eq(com.sancan.meal.plan.dal.dataobject.PlanItemDO::getUserId, memberId)
                        .eq(com.sancan.meal.plan.dal.dataobject.PlanItemDO::getServiceDate, serviceDate)
                        .eq(com.sancan.meal.plan.dal.dataobject.PlanItemDO::getMealType, "LUNCH"));
        lunch.setDishVersionId(peanutVersionId);
        planItemMapper.updateById(lunch);

        mockMvc.perform(post("/app/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "serviceDate", serviceDate.toString(),
                                "contactName", "张三", "contactPhone", "13800000000",
                                "address", "新乡市红旗区示例路 1 号"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1404));
    }

    @Test
    @DisplayName("同一服务日不能重复下单")
    void duplicateActiveOrderRejected() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        createOrder(null);
        mockMvc.perform(post("/app/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "serviceDate", serviceDate.toString(),
                                "contactName", "张三", "contactPhone", "13800000000",
                                "address", "新乡市红旗区示例路 1 号"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1401));
    }

    @Test
    @DisplayName("P09/P10 发布新定价版本后，已确认订单与锁单快照金额都不变")
    void publishingNewVersionDoesNotRepriceConfirmedOrder() throws Exception {
        grantCredit(new BigDecimal("500.00"));
        JsonNode order = createOrder(null);
        long orderId = order.path("id").asLong();
        BigDecimal original = new BigDecimal(order.path("originalAmount").asText());
        String pricingVersionNo = order.path("pricingVersionNo").asText();
        assertThat(pricingVersionNo).isNotBlank();

        // 后台发布一版更贵的基础价
        long draftId = data(admin(post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "O-" + SEQ.incrementAndGet(),
                "dailyBasePrice", 99,
                "extraProteinPricePer10g", 1.90))).asLong();
        data(admin(post("/admin/meal/pricing/versions/" + draftId + "/review")));
        data(admin(post("/admin/meal/pricing/versions/" + draftId + "/publish")));

        JsonNode afterPublish = data(admin(get("/admin/meal/orders/" + orderId)));
        assertThat(new BigDecimal(afterPublish.path("originalAmount").asText()))
                .isEqualByComparingTo(original);
        assertThat(afterPublish.path("pricingVersionNo").asText()).isEqualTo(pricingVersionNo);

        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));

        JsonNode locked = data(admin(get("/admin/meal/orders/" + orderId)));
        assertThat(locked.path("status").asText()).isEqualTo("LOCKED");
        assertThat(new BigDecimal(locked.path("balanceAmount").asText())).isEqualByComparingTo(original);
        assertThat(locked.path("pricingVersionNo").asText()).isEqualTo(pricingVersionNo);
        assertThat(locked.path("rawPrice").asDouble()).isGreaterThan(0);
    }

    // ==================== 数据准备 ====================

    private JsonNode createOrder(Long couponId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceDate", serviceDate.toString());
        body.put("contactName", "张三");
        body.put("contactPhone", "13800000000");
        body.put("address", "新乡市红旗区示例路 1 号");
        if (couponId != null) {
            body.put("couponId", couponId);
        }
        return data(member(post("/app/orders"), body));
    }

    private long createCouponTemplate() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "满50减10-" + SEQ.incrementAndGet());
        body.put("type", "CASH");
        body.put("thresholdAmount", 50);
        body.put("discountAmount", 10);
        body.put("validDays", 30);
        return data(admin(post("/admin/meal/coupon-templates"), body)).asLong();
    }

    private void grantCredit(BigDecimal amount) throws Exception {
        data(admin(post("/admin/meal/wallet/" + memberId + "/credit"),
                Map.of("amount", amount, "reason", "阶段一内部体验")));
    }

    private void preparePublishedMenu(LocalDate weekStart) throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-P3-" + tag, "26.00", "0.90", null);
        long rice = ingredient("杂粮米-P3-" + tag, "8.00", "1.00", null);
        long peanut = ingredient("花生碎-P3-" + tag, "30.00", "1.00", "花生");

        long breakfast = dish("厚蛋烧-P3-" + tag, "早餐");
        long v1 = version(breakfast, 320, 420, 28, List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long lunch = dish("鸡腿饭-P3-" + tag, "午餐");
        long v2 = version(lunch, 720, 760, 42, List.of(
                Map.of("ingredientId", chicken, "netWeightG", 150),
                Map.of("ingredientId", rice, "netWeightG", 180)));
        long dinner = dish("三文鱼-P3-" + tag, "晚餐");
        long v3 = version(dinner, 640, 700, 45, List.of(
                Map.of("ingredientId", chicken, "netWeightG", 160),
                Map.of("ingredientId", rice, "netWeightG", 120)));
        long peanutDish = dish("花生拌面-P3-" + tag, "午餐");
        peanutVersionId = version(peanutDish, 600, 700, 40,
                List.of(Map.of("ingredientId", peanut, "netWeightG", 50)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "P3 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-P3-" + tag))).asLong();

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", v1));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", v2));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", v3));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));
    }

    private void onboard(String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gender", "MALE");
        body.put("birthDate", LocalDate.now().minusYears(30).toString());
        body.put("heightCm", 175);
        body.put("weightKg", 75);
        body.put("activityLevel", "MODERATE");
        body.put("goal", "MAINTAIN");
        body.put("appetiteLevel", "NORMAL");
        body.put("allergies", "花生");
        mockMvc.perform(put("/app/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(0));
    }

    private long me() throws Exception {
        return data(member(get("/app/me"))).path("id").asLong();
    }

    private long ingredient(String name, String price, String edibleRate, String allergens) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("category", "主料");
        body.put("purchaseUnit", "kg");
        body.put("usageUnit", "kg");
        body.put("conversionRate", 1);
        body.put("edibleRate", new BigDecimal(edibleRate));
        body.put("movingAvgPrice", new BigDecimal(price));
        if (allergens != null) {
            body.put("allergens", allergens);
        }
        body.put("status", 0);
        return data(admin(post("/admin/meal/ingredients"), body)).asLong();
    }

    private long dish(String name, String category) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("category", category);
        body.put("description", name);
        body.put("storageMethod", "0-4℃ 冷藏");
        body.put("reheatMethod", "微波 90 秒");
        body.put("status", 0);
        return data(admin(post("/admin/meal/dishes"), body)).asLong();
    }

    private long version(long dishId, int weight, int kcal, int protein, List<Map<String, Object>> bom) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("netWeightG", weight);
        body.put("kcal", kcal);
        body.put("proteinG", protein);
        body.put("carbG", protein * 2);
        body.put("fatG", 15);
        body.put("bom", bom);
        return data(admin(post("/admin/meal/dishes/" + dishId + "/versions"), body)).asLong();
    }

    private String adminToken() throws Exception {
        if (adminUserMapper.selectByUsername("admin") == null) {
            AdminUserDO user = new AdminUserDO();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setNickname("超级管理员");
            user.setRole("SUPER_ADMIN");
            user.setStatus(0);
            adminUserMapper.insert(user);
        }
        return call(post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "admin123"))), null)
                .path("data").path("token").asText();
    }

    private String memberToken(String code) throws Exception {
        return call(post("/app/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "nickname", "交易测试"))), null)
                .path("data").path("token").asText();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return admin(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken);
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return member(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode data(MockHttpServletRequestBuilder builder) throws Exception {
        JsonNode root = call(builder, null);
        assertThat(root.path("code").asInt()).as("接口返回异常: " + root).isZero();
        return root.path("data");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
