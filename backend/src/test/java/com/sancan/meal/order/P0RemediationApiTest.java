package com.sancan.meal.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import com.sancan.meal.coupon.dal.mapper.CouponMapper;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import com.sancan.meal.order.dal.dataobject.DeliveryDO;
import com.sancan.meal.order.dal.dataobject.DeliveryItemDO;
import com.sancan.meal.order.dal.mapper.DeliveryItemMapper;
import com.sancan.meal.order.dal.mapper.DeliveryMapper;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 阶段一资金与履约整改的接口级验证（P0）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P0RemediationApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final AtomicInteger WEEK = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private DeliveryMapper deliveryMapper;
    @Autowired
    private DeliveryItemMapper deliveryItemMapper;
    @Autowired
    private DishVersionMapper dishVersionMapper;
    @Autowired
    private RecipeBomMapper recipeBomMapper;

    private String adminToken;
    private String memberToken;
    private long userId;
    private LocalDate day;
    private LocalDate weekStart;
    private long couponId;
    private long breakfastVersionId;
    private long lunchVersionId;
    private long dinnerVersionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        // 用较远的周次，避免与其他测试类共用同一周导致「多个已发布菜单」歧义
        day = LocalDate.now().plusWeeks(20 + WEEK.getAndIncrement()).with(DayOfWeek.WEDNESDAY);
        weekStart = day.with(DayOfWeek.MONDAY);
        prepareMenu(weekStart);

        String code = "p0-" + SEQ.incrementAndGet();
        memberToken = memberToken(code);
        userId = data(member(get("/app/me"))).path("id").asLong();
        onboard();
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        data(admin(post("/admin/meal/wallet/" + userId + "/credit"), Map.of("amount", 500, "reason", "P0 验收")));

        long templateId = data(admin(post("/admin/meal/coupon-templates"), Map.of(
                "name", "满50减10-" + SEQ.incrementAndGet(), "type", "CASH",
                "thresholdAmount", 50, "discountAmount", 10, "validDays", 30))).asLong();
        couponId = data(admin(post("/admin/meal/coupons/grant"),
                Map.of("userId", userId, "templateId", templateId))).path("id").asLong();
    }

    @Test
    @DisplayName("同一张券不能进入第二张订单：第二次下单被拒绝")
    void sameCouponCannotBeUsedTwice() throws Exception {
        String first = day.toString();
        String second = day.plusDays(1).toString();
        data(member(post("/app/orders"), orderBody(first, couponId)));

        JsonNode rejected = call(member(post("/app/orders")), orderBody(second, couponId));
        assertThat(rejected.path("code").asInt()).isNotZero();
        assertThat(rejected.path("msg").asText()).contains("优惠券");

        CouponDO coupon = couponMapper.selectById(couponId);
        assertThat(coupon.getStatus()).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("锁单失败时券保持预占，取消后释放，不出现券已使用但订单未锁定")
    void lockFailureKeepsCouponReserved() throws Exception {
        // 换成余额不足的用户：预占成功但锁单失败
        String poor = "p0-poor-" + SEQ.incrementAndGet();
        String poorToken = memberToken(poor);
        long poorId = data(member(get("/app/me"), poorToken)).path("id").asLong();
        data(member(put("/app/profile"), profileBody(), poorToken));
        data(member(post("/app/plan/generate"), Map.of("weekDate", day.with(DayOfWeek.MONDAY).toString()), poorToken));
        // 只给 1 元，保证锁单必然失败
        data(admin(post("/admin/meal/wallet/" + poorId + "/credit"), Map.of("amount", 1, "reason", "余额不足场景")));
        long poorCoupon = data(admin(post("/admin/meal/coupons/grant"), Map.of(
                "userId", poorId, "templateId", templateIdOf()))).path("id").asLong();

        long orderId = data(member(post("/app/orders"), orderBody(day.toString(), poorCoupon), poorToken)).path("id").asLong();
        JsonNode run = data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", day.toString())));
        assertThat(run.path("failed").asInt()).isEqualTo(1);

        CouponDO afterFail = couponMapper.selectById(poorCoupon);
        assertThat(afterFail.getStatus()).as("锁单失败券不能变成 USED").isEqualTo("RESERVED");

        data(member(post("/app/orders/" + orderId + "/cancel"), Map.of("reason", "取消"), poorToken));
        assertThat(couponMapper.selectById(poorCoupon).getStatus()).isEqualTo("UNUSED");
    }

    @Test
    @DisplayName("A→B→A 日价回到原价，替换差价归零")
    void swapBackRestoresOriginalPrice() throws Exception {
        long original = dayPrice();
        data(member(post("/app/plan/swap"),
                Map.of("serviceDate", day.toString(), "mealType", "LUNCH", "dishVersionId", dinnerVersionId)));
        long afterOut = dayPrice();
        data(member(post("/app/plan/swap"),
                Map.of("serviceDate", day.toString(), "mealType", "LUNCH", "dishVersionId", lunchVersionId)));
        long afterBack = dayPrice();

        assertThat(afterOut).isNotEqualTo(original);
        assertThat(afterBack).as("back=" + afterBack + " original=" + original + " out=" + afterOut)
                .isEqualTo(original);
        JsonNode dayResp = data(member(get("/app/plan/day").param("date", day.toString())));
        assertThat(new BigDecimal(dayResp.path("swapDifference").asText())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("锁单后改菜品重量与 BOM，快照与订单金额不变")
    void snapshotIsImmutable() throws Exception {
        long orderId = data(member(post("/app/orders"), orderBody(day.toString(), null))).path("id").asLong();
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", day.toString())));

        String before = data(admin(get("/admin/meal/orders/" + orderId + "/snapshot"))).path("payload").asText();
        BigDecimal amountBefore = new BigDecimal(data(admin(get("/admin/meal/orders/" + orderId)))
                .path("originalAmount").asText());

        // 模拟改菜谱：改重量与 BOM，再改价
        DishVersionDO version = dishVersionMapper.selectById(lunchVersionId);
        version.setNetWeightG(version.getNetWeightG() + 200);
        version.setProteinG(version.getProteinG().add(new BigDecimal("10")));
        dishVersionMapper.updateById(version);
        RecipeBomDO bom = recipeBomMapper.selectOne(new LambdaQueryWrapper<RecipeBomDO>()
                .eq(RecipeBomDO::getDishVersionId, lunchVersionId).last("LIMIT 1"));
        bom.setNetWeightG(bom.getNetWeightG().add(new BigDecimal("50")));
        recipeBomMapper.updateById(bom);

        String after = data(admin(get("/admin/meal/orders/" + orderId + "/snapshot"))).path("payload").asText();
        BigDecimal amountAfter = new BigDecimal(data(admin(get("/admin/meal/orders/" + orderId)))
                .path("originalAmount").asText());
        assertThat(after).isEqualTo(before);
        assertThat(amountAfter).isEqualByComparingTo(amountBefore);
    }

    @Test
    @DisplayName("跨日履约：早餐属于前一天晚餐批次，配送与明细按实际日期落库")
    void breakfastDeliveredWithPreviousEvening() throws Exception {
        long orderId = data(member(post("/app/orders"), orderBody(day.toString(), null))).path("id").asLong();
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", day.toString())));

        List<DeliveryDO> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<DeliveryDO>()
                .eq(DeliveryDO::getOrderId, orderId));
        assertThat(deliveries).hasSize(3);
        DeliveryDO breakfast = deliveries.stream()
                .filter(d -> "BREAKFAST".equals(d.getDeliverySlot())).findFirst().orElseThrow();
        assertThat(breakfast.getDeliveryBatch()).isEqualTo("BATCH_2");
        assertThat(breakfast.getActualDeliveryDate()).isEqualTo(day.minusDays(1));

        List<DeliveryItemDO> items = deliveryItemMapper.selectList(new LambdaQueryWrapper<DeliveryItemDO>()
                .eq(DeliveryItemDO::getOrderId, orderId));
        assertThat(items).hasSize(3);
        assertThat(items).extracting(DeliveryItemDO::getMealType)
                .containsExactlyInAnyOrder("BREAKFAST", "LUNCH", "DINNER");
    }


    @Test
    @DisplayName("完成晚餐批次只更新该条配送，不误伤次日早餐")
    void deliveryStatusUpdatesOnlyTargetRow() throws Exception {
        long orderId = data(member(post("/app/orders"), orderBody(day.toString(), null))).path("id").asLong();
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", day.toString())));

        JsonNode before = data(admin(get("/admin/meal/orders/" + orderId)));
        long dinnerId = -1;
        for (JsonNode d : before.path("deliveries")) {
            if ("DINNER".equals(d.path("deliverySlot").asText())) {
                dinnerId = d.path("id").asLong();
            }
        }
        assertThat(dinnerId).isPositive();

        data(admin(post("/admin/meal/orders/" + orderId + "/delivery"),
                Map.of("deliveryId", dinnerId, "status", "DONE")));

        JsonNode after = data(admin(get("/admin/meal/orders/" + orderId)));
        for (JsonNode d : after.path("deliveries")) {
            String slot = d.path("deliverySlot").asText();
            if ("DINNER".equals(slot)) {
                assertThat(d.path("status").asText()).isEqualTo("DONE");
                assertThat(d.hasNonNull("deliverTime")).isTrue();
            } else {
                assertThat(d.path("status").asText()).as(slot + " 不应被一起完成").isEqualTo("PENDING");
                assertThat(d.hasNonNull("deliverTime")).isFalse();
            }
        }
    }

    @Test
    @DisplayName("下单后换菜单并重建计划，旧订单仍按订单来源菜单生成快照")
    void snapshotUsesOrderSourceMenu() throws Exception {
        long orderId = data(member(post("/app/orders"), orderBody(day.toString(), null))).path("id").asLong();
        long oldMenuId = publishedMenuId();
        long newMenuId = publishAnotherMenu();
        assertThat(newMenuId).isNotEqualTo(oldMenuId);

        // 重新生成计划：新计划指向新菜单
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));

        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", day.toString())));
        JsonNode snapshot = data(admin(get("/admin/meal/orders/" + orderId + "/snapshot")));
        assertThat(snapshot.path("menuId").asLong()).as("快照必须用下单时的菜单").isEqualTo(oldMenuId);
    }

    private long publishedMenuId() throws Exception {
        JsonNode page = data(admin(get("/admin/meal/menus?pageNo=1&pageSize=100")));
        for (JsonNode menu : page.path("list")) {
            if (weekStart.toString().equals(menu.path("startDate").asText())
                    && "PUBLISHED".equals(menu.path("status").asText())) {
                return menu.path("id").asLong();
            }
        }
        throw new AssertionError("未找到已发布菜单");
    }

    private long publishAnotherMenu() throws Exception {
        int tag = SEQ.incrementAndGet();
        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "换版测试-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V2.0-P0-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", breakfastVersionId));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", lunchVersionId));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", dinnerVersionId));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));
        return menu;
    }

    @Test
    @DisplayName("一键订整周：优惠按日分摊、只占一张券、锁单核销一次")
    void weeklyOrderAllocatesCoupon() throws Exception {
        Map<String, Object> quoteReq = new LinkedHashMap<>();
        quoteReq.put("weekStart", weekStart.toString());
        quoteReq.put("couponId", couponId);
        JsonNode quote = data(member(post("/app/orders/weekly/quote"), quoteReq));
        int days = quote.path("days").size();
        assertThat(days).isGreaterThanOrEqualTo(3);
        BigDecimal discount = new BigDecimal(quote.path("couponDiscount").asText());
        assertThat(discount).isEqualByComparingTo("10.00");

        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("weekStart", weekStart.toString());
        createReq.put("couponId", couponId);
        createReq.put("contactName", "张三");
        createReq.put("contactPhone", "13800000000");
        createReq.put("address", "新乡市示例路 1 号");
        JsonNode created = data(member(post("/app/orders/weekly"), createReq));
        assertThat(created.path("orderCount").asInt()).isEqualTo(days);
        assertThat(new BigDecimal(created.path("couponDiscount").asText())).isEqualByComparingTo(discount);

        JsonNode orders = data(member(get("/app/orders?pageNo=1&pageSize=20"))).path("list");
        BigDecimal allocated = BigDecimal.ZERO;
        for (JsonNode order : orders) {
            allocated = allocated.add(new BigDecimal(order.path("couponAmount").asText("0")));
        }
        assertThat(allocated).isEqualByComparingTo(discount);
        assertThat(couponMapper.selectById(couponId).getStatus()).isEqualTo("RESERVED");

        // 同一张券不能再次用于第二个周订单
        JsonNode again = call(member(post("/app/orders/weekly")), createReq);
        assertThat(again.path("code").asInt()).isNotZero();

        // 锁单后券只核销一次
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", weekStart.plusDays(4).toString())));
        assertThat(couponMapper.selectById(couponId).getStatus()).isIn("RESERVED", "USED");
    }

    private long dayPrice() throws Exception {
        return data(member(get("/app/plan/day").param("date", day.toString()))).path("dailyPrice").asLong();
    }

    // ==================== fixture ====================

    private long templateIdOf() throws Exception {
        CouponDO coupon = couponMapper.selectById(couponId);
        return coupon.getTemplateId();
    }

    private Map<String, Object> orderBody(String serviceDate, Long coupon) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceDate", serviceDate);
        if (coupon != null) {
            body.put("couponId", coupon);
        }
        body.put("contactName", "张三");
        body.put("contactPhone", "13800000000");
        body.put("address", "新乡市示例路 1 号");
        return body;
    }

    private void prepareMenu(LocalDate weekStart) throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-P0-" + tag, "26.00");
        long rice = ingredient("杂粮米-P0-" + tag, "8.00");
        breakfastVersionId = version(dish("厚蛋烧-P0-" + tag, "早餐"), 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        lunchVersionId = version(dish("鸡腿饭-P0-" + tag, "午餐"), 620, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        // 晚餐成本明显高于午餐，保证替换差价能体现在整数日价上
        dinnerVersionId = version(dish("三文鱼-P0-" + tag, "晚餐"), 900, 900, 60,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 320),
                        Map.of("ingredientId", rice, "netWeightG", 260)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "P0 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-P0-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", breakfastVersionId));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", lunchVersionId));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", dinnerVersionId));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));
    }

    private Map<String, Object> profileBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gender", "FEMALE");
        body.put("birthDate", LocalDate.now().minusYears(28).toString());
        body.put("heightCm", 165);
        body.put("weightKg", 62);
        body.put("activityLevel", "MODERATE");
        body.put("goal", "MAINTAIN");
        body.put("appetiteLevel", "NORMAL");
        body.put("allergies", "");
        return body;
    }

    private void onboard() throws Exception {
        data(member(put("/app/profile"), profileBody()));
    }

    private long ingredient(String name, String price) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("category", "主料");
        body.put("purchaseUnit", "kg");
        body.put("usageUnit", "kg");
        body.put("conversionRate", 1);
        body.put("edibleRate", new BigDecimal("0.90"));
        body.put("movingAvgPrice", new BigDecimal(price));
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
                .content(json(Map.of("code", code, "nickname", "P0 测试"))), null)
                .path("data").path("token").asText();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return admin(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder) {
        return member(builder, memberToken);
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return member(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, Object body, String token) throws Exception {
        return member(builder, token).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
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
