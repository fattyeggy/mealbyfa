package com.sancan.meal.plan;

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
 * P4.6 边界与复核门禁验收（R01–R07、R11、R13）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P46ReviewGateTest {

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
    private LocalDate weekStart;
    private LocalDate serviceDate;
    private long menuId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        weekStart = LocalDate.now().plusWeeks(8 + NEXT_WEEK.getAndIncrement()).with(java.time.DayOfWeek.MONDAY);
        serviceDate = weekStart.plusDays(2);
        menuId = preparePublishedMenu();
        publishBaselinePricing();
    }

    @Test
    @DisplayName("R01/R02 142–150kg 用户都能生成计划，不再返回 500")
    void heavyUsersDoNotFail() throws Exception {
        for (int weight : new int[]{142, 144, 146, 148, 149, 150}) {
            String token = heavyMember(weight);
            JsonNode resp = call(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), token), null);
            assertThat(resp.path("code").asInt()).as(weight + "kg").isZero();
            assertThat(resp.path("data").path("needsReview").asBoolean()).isTrue();
            assertThat(resp.path("data").path("reviews")).isNotEmpty();
        }
    }

    @Test
    @DisplayName("R07 原始目标蛋白不被夹取：150kg 返回原始 225g、计划 220g，并记录 PROTEIN_RANGE")
    void rawProteinIsPreserved() throws Exception {
        String token = heavyMember(150);
        JsonNode plan = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), token));

        assertThat(plan.path("rawTargetProteinG").asDouble()).isEqualTo(225.00);
        assertThat(plan.path("dailyProteinG").asDouble()).isEqualTo(220.00);
        assertThat(plan.path("needsReview").asBoolean()).isTrue();
        assertThat(reviewTypes(plan)).contains("PROTEIN_RANGE");
    }

    @Test
    @DisplayName("R03 份量触界也会留复核，且复核文本不再导致字段溢出")
    void scaleClampIsRecorded() throws Exception {
        String token = heavyMember(150);
        JsonNode plan = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), token));
        // 220g 目标下午餐需要 88g 蛋白，标准菜品 42g，缩放 2.1 倍触发上限
        assertThat(reviewTypes(plan)).contains("SCALE_CLAMP");
        assertThat(plan.path("reviews")).hasSizeGreaterThan(5);
        // 摘要完整保存（TEXT 字段），能读回中文原因
        assertThat(plan.path("reviewNotes").get(0).asText()).contains("超出");
    }

    @Test
    @DisplayName("R05 存在未处理复核时下单被拒绝，处理完才允许下单")
    void orderBlockedUntilReviewsResolved() throws Exception {
        String token = heavyMember(150);
        JsonNode plan = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString(), "x", ""), token)
                );
        long planId = plan.path("planId").asLong();
        long userId = dataAs(get("/app/me"), token).path("id").asLong();
        data(admin(post("/admin/meal/wallet/" + userId + "/credit"),
                Map.of("amount", 500, "reason", "P4.6 测试")));

        // 先拒绝
        mockMvc.perform(post("/app/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1208));

        // 处理一条，仍有剩余 → 继续拒绝
        JsonNode reviews = data(admin(get("/admin/meal/plans/" + planId + "/reviews").param("status", "PENDING")));
        data(admin(post("/admin/meal/reviews/" + reviews.get(0).path("id").asLong() + "/resolve"),
                Map.of("note", "已人工确认")));
        mockMvc.perform(post("/app/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1208));

        // 全部处理完 → 允许下单
        JsonNode remaining = data(admin(get("/admin/meal/plans/" + planId + "/reviews").param("status", "PENDING")));
        for (JsonNode review : remaining) {
            data(admin(post("/admin/meal/reviews/" + review.path("id").asLong() + "/resolve"),
                    Map.of("note", "已人工确认")));
        }
        JsonNode order = data(member(post("/app/orders"), orderBody(), token));
        assertThat(order.path("statusLabel").asText()).isEqualTo("待锁定");
    }

    @Test
    @DisplayName("R06 复核完成后重建计划，复核事项重新打开")
    void regeneratingPlanReopensReviews() throws Exception {
        String token = heavyMember(150);
        JsonNode first = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), token));
        long firstPlanId = first.path("planId").asLong();
        JsonNode reviews = data(admin(get("/admin/meal/plans/" + firstPlanId + "/reviews").param("status", "PENDING")));
        for (JsonNode review : reviews) {
            data(admin(post("/admin/meal/reviews/" + review.path("id").asLong() + "/resolve"),
                    Map.of("note", "已处理")));
        }
        assertThat(data(admin(get("/admin/meal/plans/" + firstPlanId + "/reviews").param("status", "PENDING"))))
                .isEmpty();

        JsonNode second = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), token));
        assertThat(second.path("planId").asLong()).isNotEqualTo(firstPlanId);
        assertThat(second.path("needsReview").asBoolean()).isTrue();
        assertThat(second.path("reviews")).isNotEmpty();
    }

    @Test
    @DisplayName("R11 非法定价版本参数被拒绝并指出字段")
    void invalidPricingVersionRejected() throws Exception {
        mockMvc.perform(post("/admin/meal/pricing/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("versionNo", "BAD-" + SEQ.incrementAndGet(),
                                "dailyBasePrice", 66,
                                "minTargetProteinG", 300,
                                "maxTargetProteinG", 100))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(400))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("蛋白范围")));

        mockMvc.perform(post("/admin/meal/pricing/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("versionNo", "BAD2-" + SEQ.incrementAndGet(),
                                "dailyBasePrice", 66,
                                "swapCostDivisor", 0))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(400))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("成本折算系数")));
    }

    @Test
    @DisplayName("R13 菜单检查明确区分公共标准菜单与个人计划蛋白口径")
    void menuCheckMessageDistinguishesRanges() throws Exception {
        JsonNode check = data(admin(get("/admin/meal/menus/" + menuId + "/check")));
        String detail = check.path("items").get(2).path("detail").asText();
        assertThat(detail).contains("公共标准菜单");
        assertThat(detail).contains("80-180");
        assertThat(detail).contains("个人计划");
        assertThat(detail).contains("220");
    }

    // ==================== 数据准备 ====================

    private List<String> reviewTypes(JsonNode plan) {
        List<String> types = new ArrayList<>();
        for (JsonNode review : plan.path("reviews")) {
            types.add(review.path("reviewType").asText());
        }
        return types;
    }

    private Map<String, Object> orderBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceDate", serviceDate.toString());
        body.put("contactName", "高蛋白测试");
        body.put("contactPhone", "13800000000");
        body.put("address", "新乡市示例路 1 号");
        return body;
    }

    private String heavyMember(int weightKg) throws Exception {
        String code = "heavy-" + weightKg + "-" + SEQ.incrementAndGet();
        String token = call(post("/app/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "nickname", weightKg + "kg"))), null)
                .path("data").path("token").asText();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("gender", "MALE");
        profile.put("birthDate", LocalDate.now().minusYears(30).toString());
        profile.put("heightCm", 175);
        profile.put("weightKg", weightKg);
        profile.put("activityLevel", "MODERATE");
        profile.put("goal", "MAINTAIN");
        profile.put("appetiteLevel", "NORMAL");
        profile.put("allergies", "");
        dataAs(put("/app/profile").contentType(MediaType.APPLICATION_JSON).content(json(profile)), token);
        return token;
    }

    private void publishBaselinePricing() throws Exception {
        long id = data(admin(post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "PB46-" + SEQ.incrementAndGet(),
                "dailyBasePrice", 66,
                "extraProteinPricePer10g", 1.90))).asLong();
        data(admin(post("/admin/meal/pricing/versions/" + id + "/review")));
        data(admin(post("/admin/meal/pricing/versions/" + id + "/publish")));
    }

    private long preparePublishedMenu() throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-P46-" + tag, "26.00", "0.90", null);
        long rice = ingredient("杂粮米-P46-" + tag, "8.00", "1.00", null);

        long v1 = version(dish("厚蛋烧-P46-" + tag, "早餐"), 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long v2 = version(dish("鸡腿饭-P46-" + tag, "午餐"), 720, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        long v3 = version(dish("三文鱼-P46-" + tag, "晚餐"), 640, 700, 45,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 160),
                        Map.of("ingredientId", rice, "netWeightG", 120)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "P4.6 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-P46-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", v1));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", v2));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", v3));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));
        return menu;
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

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return admin(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, Object body, String token)
            throws Exception {
        return member(builder, token).contentType(MediaType.APPLICATION_JSON).content(json(body));
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
