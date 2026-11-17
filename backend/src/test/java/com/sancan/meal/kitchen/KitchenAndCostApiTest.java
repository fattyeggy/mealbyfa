package com.sancan.meal.kitchen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
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

/**
 * 后场域验收：生产合并、食材需求、领料与产量、三层成本报表、临时替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KitchenAndCostApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final AtomicInteger NEXT_WEEK = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private LocalDate serviceDate;
    private long lunchVersionId;
    private long peanutVersionId;
    private long memberAId;
    private long memberBId;
    private long orderA;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        serviceDate = LocalDate.now().plusWeeks(2 + NEXT_WEEK.getAndIncrement()).with(DayOfWeek.THURSDAY);
        LocalDate weekStart = serviceDate.with(DayOfWeek.MONDAY);
        List<Long> versions = preparePublishedMenu(weekStart);
        lunchVersionId = versions.get(1);
        peanutVersionId = versions.get(3);

        String codeA = "kitchen-a-" + SEQ.incrementAndGet();
        String codeB = "kitchen-b-" + SEQ.incrementAndGet();
        memberAId = prepareMember(codeA, weekStart);
        memberBId = prepareMember(codeB, weekStart);
        orderA = createAndLock(codeA);
        createAndLock(codeB);
    }

    @Test
    @DisplayName("两个订单的同一餐次合并成一个生产批次，先合并再生产")
    void generateMergesOrders() throws Exception {
        JsonNode summary = data(admin(post("/admin/meal/kitchen/generate")
                .param("serviceDate", serviceDate.toString())));

        // 当日只含午餐与晚餐（早餐随次日生产日的 BATCH_2），共 2 个批次、每批次 2 份
        assertThat(summary.path("batches")).hasSize(2);
        assertThat(summary.path("totalPlanQty").asInt()).isEqualTo(4);
        for (JsonNode batch : summary.path("batches")) {
            assertThat(batch.path("planQty").asInt()).isEqualTo(2);
            assertThat(batch.path("status").asText()).isEqualTo("PLANNED");
        }
        assertThat(summary.path("labels")).hasSize(4);
        assertThat(summary.path("labels").get(0).path("contactName").asText()).isNotBlank();
    }

    @Test
    @DisplayName("重复生成不会产生重复批次，份数按最新订单重算")
    void generateIsIdempotent() throws Exception {
        data(admin(post("/admin/meal/kitchen/generate").param("serviceDate", serviceDate.toString())));
        JsonNode second = data(admin(post("/admin/meal/kitchen/generate")
                .param("serviceDate", serviceDate.toString())));
        assertThat(second.path("batches")).hasSize(2);
        assertThat(second.path("totalPlanQty").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("食材需求按 BOM × 份数推导，并按可食率折算采购重量")
    void materialDemandMath() throws Exception {
        data(admin(post("/admin/meal/kitchen/generate").param("serviceDate", serviceDate.toString())));
        JsonNode demand = data(admin(get("/admin/meal/kitchen/material-demand")
                .param("serviceDate", serviceDate.toString())));

        JsonNode chicken = findItem(demand, "鸡胸肉-P4");
        BigDecimal edible = new BigDecimal(chicken.path("edibleG").asText());
        // 个人份量按 45/42 等比例放大，理论用量必须随之放大，而不是按标准 BOM 计
        assertThat(edible).isGreaterThan(new BigDecimal("500"));
        // 采购重量 = 可食生重 ÷ 可食率 90%
        assertThat(new BigDecimal(chicken.path("purchaseG").asText()))
                .isEqualByComparingTo(edible.divide(new BigDecimal("0.90"), 2, java.math.RoundingMode.HALF_UP));
        // 26 元/kg × 可食生重 ÷ 0.9
        BigDecimal expectedCost = new BigDecimal("26.00").multiply(edible)
                .divide(new BigDecimal("1000"), 6, java.math.RoundingMode.HALF_UP)
                .divide(new BigDecimal("0.90"), 4, java.math.RoundingMode.HALF_UP);
        assertThat(new BigDecimal(chicken.path("cost").asText()))
                .isEqualByComparingTo(expectedCost);
        // 尚未登记领料：响应开启了 non_null，字段直接缺省
        assertThat(chicken.has("actualG")).isFalse();
    }

    @Test
    @DisplayName("登记领料与成品产量后，批次与食材需求同步更新")
    void recordIssueAndOutput() throws Exception {
        JsonNode summary = data(admin(post("/admin/meal/kitchen/generate")
                .param("serviceDate", serviceDate.toString())));
        long batchId = summary.path("batches").get(0).path("id").asLong();
        long ingredientId = findItem(data(admin(get("/admin/meal/kitchen/material-demand")
                .param("serviceDate", serviceDate.toString()))), "鸡胸肉-P4").path("ingredientId").asLong();

        data(admin(post("/admin/meal/kitchen/batches/" + batchId + "/issue"), Map.of(
                "ingredientId", ingredientId, "actualG", 200, "wasteG", 10, "reason", "边角料")));
        data(admin(post("/admin/meal/kitchen/batches/" + batchId + "/output"), Map.of(
                "actualQty", 2, "wasteQty", 1, "remark", "一份破损")));

        JsonNode after = data(admin(get("/admin/meal/kitchen/summary")
                .param("serviceDate", serviceDate.toString())));
        JsonNode batch = after.path("batches").get(0);
        assertThat(batch.path("actualQty").asInt()).isEqualTo(2);
        assertThat(batch.path("wasteQty").asInt()).isEqualTo(1);
        assertThat(batch.path("status").asText()).isEqualTo("PREPARING");

        JsonNode demand = data(admin(get("/admin/meal/kitchen/material-demand")
                .param("serviceDate", serviceDate.toString())));
        JsonNode chicken = findItem(demand, "鸡胸肉-P4");
        assertThat(new BigDecimal(chicken.path("actualG").asText())).isEqualByComparingTo("200");
        assertThat(new BigDecimal(chicken.path("wasteG").asText())).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("成本报表：三层成本与毛利可对账")
    void costReportReconciles() throws Exception {
        JsonNode orderDetail = data(admin(get("/admin/meal/orders/" + orderA)));
        BigDecimal orderARevenue = new BigDecimal(orderDetail.path("balanceAmount").asText());
        BigDecimal revenue = orderARevenue.multiply(BigDecimal.valueOf(2));

        JsonNode report = data(admin(get("/admin/meal/cost/report")
                .param("start", serviceDate.toString())
                .param("end", serviceDate.toString())));
        JsonNode totals = report.path("totals");

        assertThat(totals.path("orders").asInt()).isEqualTo(2);
        assertThat(totals.path("meals").asInt()).isEqualTo(6);
        assertThat(new BigDecimal(totals.path("revenue").asText())).isEqualByComparingTo(revenue);
        // 包装 2 元 × 6 餐；配送 12 元 × 2 单；固定 3 元 × 2 单
        assertThat(new BigDecimal(totals.path("packageCost").asText())).isEqualByComparingTo("12.00");
        assertThat(new BigDecimal(totals.path("deliveryCost").asText())).isEqualByComparingTo("24.00");
        assertThat(new BigDecimal(totals.path("fixedCost").asText())).isEqualByComparingTo("6.00");
        assertThat(new BigDecimal(totals.path("paymentFee").asText()))
                .isEqualByComparingTo(revenue.multiply(new BigDecimal("0.0060")).setScale(2, java.math.RoundingMode.HALF_UP));

        BigDecimal food = new BigDecimal(totals.path("foodCost").asText());
        BigDecimal total = new BigDecimal(totals.path("totalCost").asText());
        BigDecimal profit = new BigDecimal(totals.path("grossProfit").asText());
        assertThat(food.add(new BigDecimal("12.00")).add(new BigDecimal("24.00"))
                .add(new BigDecimal(totals.path("paymentFee").asText())).add(new BigDecimal("6.00")))
                .isEqualByComparingTo(total);
        assertThat(revenue.subtract(total)).isEqualByComparingTo(profit);
    }

    @Test
    @DisplayName("领料差异进入成本报表：实际用料高于理论时差异为正")
    void materialDifferenceAppearsInReport() throws Exception {
        data(admin(post("/admin/meal/kitchen/generate").param("serviceDate", serviceDate.toString())));
        JsonNode summary = data(admin(get("/admin/meal/kitchen/summary")
                .param("serviceDate", serviceDate.toString())));
        long batchId = summary.path("batches").get(0).path("id").asLong();
        JsonNode demand = data(admin(get("/admin/meal/kitchen/material-demand")
                .param("serviceDate", serviceDate.toString())));
        JsonNode item = demand.path("items").get(0);
        long ingredientId = item.path("ingredientId").asLong();
        java.math.BigDecimal edible = new java.math.BigDecimal(item.path("edibleG").asText());

        // 实际领料比理论多 20%
        data(admin(post("/admin/meal/kitchen/batches/" + batchId + "/issue"), Map.of(
                "ingredientId", ingredientId,
                "actualG", edible.multiply(new java.math.BigDecimal("1.2")),
                "wasteG", 0,
                "reason", "切配损耗偏高")));

        JsonNode totals = data(admin(get("/admin/meal/cost/report")
                .param("start", serviceDate.toString())
                .param("end", serviceDate.toString()))).path("totals");

        assertThat(totals.path("hasMaterialIssue").asBoolean()).isTrue();
        java.math.BigDecimal theoretical = new java.math.BigDecimal(totals.path("materialTheoreticalCost").asText());
        java.math.BigDecimal actual = new java.math.BigDecimal(totals.path("materialActualCost").asText());
        java.math.BigDecimal diff = new java.math.BigDecimal(totals.path("materialDiff").asText());
        assertThat(theoretical.signum()).isPositive();
        assertThat(actual.signum()).isPositive();
        assertThat(diff).isEqualByComparingTo(actual.subtract(theoretical));
    }

    @Test
    @DisplayName("临时替换保留审批与通知状态，且拦截用户过敏原")
    void substitutionKeepsApprovalAndNotify() throws Exception {
        JsonNode record = data(admin(post("/admin/meal/orders/" + orderA + "/substitutions"), Map.of(
                "mealType", "LUNCH", "toDishVersionId", lunchVersionId, "reason", "食材到货差异")));
        long id = record.path("id").asLong();
        assertThat(record.path("notifyStatus").asText()).isEqualTo("PENDING");
        assertThat(record.path("approvalBy").asLong()).isPositive();

        data(admin(post("/admin/meal/substitutions/" + id + "/notify")));
        JsonNode after = data(admin(get("/admin/meal/orders/" + orderA + "/substitutions")));
        assertThat(after.get(0).path("notifyStatus").asText()).isEqualTo("SENT");

        // 会员 A 有花生过敏，替换到含花生的菜品应被拒绝
        mockMvc.perform(post("/admin/meal/orders/" + orderA + "/substitutions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("mealType", "LUNCH",
                                "toDishVersionId", peanutVersionId, "reason", "测试"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1206));
    }

    // ==================== 数据准备 ====================

    private long prepareMember(String code, LocalDate weekStart) throws Exception {
        String token = memberToken(code);
        long userId = dataAs(get("/app/me"), token).path("id").asLong();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("gender", "MALE");
        profile.put("birthDate", LocalDate.now().minusYears(30).toString());
        profile.put("heightCm", 175);
        profile.put("weightKg", 75);
        profile.put("activityLevel", "MODERATE");
        profile.put("goal", "MAINTAIN");
        profile.put("appetiteLevel", "NORMAL");
        profile.put("allergies", "花生");
        dataAs(put("/app/profile").contentType(MediaType.APPLICATION_JSON).content(json(profile)), token);
        dataAs(post("/app/plan/generate").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("weekDate", weekStart.toString()))), token);
        data(admin(post("/admin/meal/wallet/" + userId + "/credit"),
                Map.of("amount", 500, "reason", "厨房测试")));
        return userId;
    }

    private long createAndLock(String code) throws Exception {
        String token = memberToken(code);
        String tag = code;
        JsonNode order = dataAs(post("/app/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("serviceDate", serviceDate.toString(),
                        "contactName", "测试" + tag, "contactPhone", "13800000000",
                        "address", "新乡市示例路 " + tag))), token);
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));
        return order.path("id").asLong();
    }

    /** @return [早餐版本, 午餐版本, 晚餐版本, 花生版本] */
    private List<Long> preparePublishedMenu(LocalDate weekStart) throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-P4-" + tag, "26.00", "0.90", null);
        long rice = ingredient("杂粮米-P4-" + tag, "8.00", "1.00", null);
        long peanut = ingredient("花生碎-P4-" + tag, "30.00", "1.00", "花生");

        long v1 = version(dish("厚蛋烧-P4-" + tag, "早餐"), 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long v2 = version(dish("鸡腿饭-P4-" + tag, "午餐"), 720, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        long v3 = version(dish("三文鱼-P4-" + tag, "晚餐"), 640, 700, 45,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 160),
                        Map.of("ingredientId", rice, "netWeightG", 120)));
        long v4 = version(dish("花生拌面-P4-" + tag, "午餐"), 600, 700, 40,
                List.of(Map.of("ingredientId", peanut, "netWeightG", 50)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "P4 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-P4-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", v1));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", v2));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", v3));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));
        return List.of(v1, v2, v3, v4);
    }

    private JsonNode findItem(JsonNode demand, String namePrefix) {
        for (JsonNode item : demand.path("items")) {
            if (item.path("ingredientName").asText().startsWith(namePrefix)) {
                return item;
            }
        }
        throw new AssertionError("未找到食材 " + namePrefix + "，实际：" + demand.path("items"));
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
        String token = call(post("/app/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "nickname", "厨房测试"))), null)
                .path("data").path("token").asText();
        if (token.isEmpty()) {
            throw new AssertionError("登录失败：" + code);
        }
        return token;
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return admin(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode dataAs(MockHttpServletRequestBuilder builder, String token) throws Exception {
        JsonNode root = call(builder, token);
        assertThat(root.path("code").asInt()).as("接口返回异常: " + root).isZero();
        return root.path("data");
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
