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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 计划与计价接口测试（计价规则 V1.0）。
 *
 * <p>本类验证价格口径：日价 = 66 + 1.90 × 加量蛋白 ÷ 10，替换按成本差 ÷0.77，
 * 三餐展示价只是日价拆分。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanApiTest {

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
    private String memberToken;
    private LocalDate weekStart;
    private LocalDate serviceDate;
    private long altLunchVersionId;
    private long peanutVersionId;
    private long zeroCostVersionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        weekStart = LocalDate.now().plusWeeks(4 + NEXT_WEEK.getAndIncrement()).with(java.time.DayOfWeek.MONDAY);
        serviceDate = weekStart.plusDays(2);
        preparePublishedMenu();
        publishBaselinePricing();
        memberToken = memberToken("plan-user-" + SEQ.incrementAndGet());
        onboard(memberToken);
    }

    @Test
    @DisplayName("75kg 维持：计划蛋白取整 113g，日价 76 元，三餐按 25/40/35 拆分")
    void generatePlanWithDailyPricing() throws Exception {
        JsonNode plan = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));

        assertThat(plan.path("dailyProteinG").asDouble()).isEqualTo(113.0);
        assertThat(plan.path("pricingVersionNo").asText()).isNotBlank();
        assertThat(plan.path("needsReview").asBoolean()).isFalse();

        JsonNode day = findDay(plan, serviceDate.toString());
        // 66 + 1.90 × (113−60) ÷ 10 = 76.07 → 76
        assertThat(day.path("dailyPrice").asInt()).isEqualTo(76);
        assertThat(day.path("rawPrice").asDouble()).isEqualTo(76.07);

        JsonNode breakfast = findMeal(day, "BREAKFAST");
        JsonNode lunch = findMeal(day, "LUNCH");
        JsonNode dinner = findMeal(day, "DINNER");
        assertThat(breakfast.path("price").asDouble()).isEqualTo(19.00);
        assertThat(lunch.path("price").asDouble()).isEqualTo(30.40);
        assertThat(dinner.path("price").asDouble()).isEqualTo(26.60);
        double sum = breakfast.path("price").asDouble() + lunch.path("price").asDouble()
                + dinner.path("price").asDouble();
        assertThat(sum).isEqualTo(76.00);

        // 午餐目标蛋白 113 × 40% = 45.20g，营养点 4.52
        assertThat(lunch.path("proteinG").asDouble()).isEqualTo(45.20);
        assertThat(lunch.path("points").asDouble()).isEqualTo(4.52);
        assertThat(day.path("totalProteinG").asDouble()).isBetween(112.0, 114.0);
    }

    @Test
    @DisplayName("未建档不能生成计划")
    void generateRequiresProfile() throws Exception {
        String freshMember = memberToken("plan-noprofile-" + SEQ.incrementAndGet());
        mockMvc.perform(post("/app/plan/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshMember)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("weekDate", weekStart.toString()))))
                .andExpect(jsonPath("$.code").value(1200));
    }

    @Test
    @DisplayName("该周菜单未发布时不能生成计划")
    void generateRequiresPublishedMenu() throws Exception {
        mockMvc.perform(post("/app/plan/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("weekDate", weekStart.plusWeeks(52).toString()))))
                .andExpect(jsonPath("$.code").value(1100));
    }

    @Test
    @DisplayName("便宜替换产生负差价：日价从 76 降到 70")
    void cheapSwapLowersDailyPrice() throws Exception {
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));

        JsonNode preview = data(member(post("/app/plan/swap/preview"), Map.of(
                "serviceDate", serviceDate.toString(),
                "mealType", "LUNCH",
                "dishVersionId", altLunchVersionId)));

        assertThat(preview.path("before").path("proteinG").asDouble()).isEqualTo(45.20);
        assertThat(preview.path("after").path("proteinG").asDouble()).isEqualTo(45.20);
        // 45.20 ÷ 60 = 0.7533，650g × 0.7533 = 489.67 → 490
        assertThat(preview.path("after").path("weightG").asInt()).isEqualTo(490);
        // 变动成本由 6.2133 降到 1.2053，差价 (1.2053−6.2133)÷0.77 = −6.5039
        assertThat(preview.path("swapDifference").asDouble()).isLessThan(0);
        assertThat(preview.path("before").path("dailyPrice").asInt()).isEqualTo(76);
        assertThat(preview.path("after").path("dailyPrice").asInt()).isEqualTo(70);
        assertThat(preview.path("priceDelta").asInt()).isEqualTo(-6);

        data(member(post("/app/plan/swap"), Map.of(
                "serviceDate", serviceDate.toString(),
                "mealType", "LUNCH",
                "dishVersionId", altLunchVersionId)));
        JsonNode day = data(member(get("/app/plan/day").param("date", serviceDate.toString())));
        assertThat(findMeal(day, "LUNCH").path("dishName").asText()).contains("香煎三文鱼");
        assertThat(day.path("dailyPrice").asInt()).isEqualTo(70);
        double sum = 0;
        for (JsonNode meal : day.path("meals")) {
            sum += meal.path("price").asDouble();
        }
        assertThat(sum).isEqualTo(70.00);
    }

    @Test
    @DisplayName("P12 连续调整不累加：换到替代菜品再换回，日价回到 76")
    void repeatedSwapDoesNotAccumulate() throws Exception {
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        long baseLunchVersionId = findMeal(
                data(member(get("/app/plan/day").param("date", serviceDate.toString()))), "LUNCH")
                .path("dishVersionId").asLong();

        data(member(post("/app/plan/swap"), Map.of("serviceDate", serviceDate.toString(),
                "mealType", "LUNCH", "dishVersionId", altLunchVersionId)));
        JsonNode swapped = data(member(get("/app/plan/day").param("date", serviceDate.toString())));
        assertThat(swapped.path("dailyPrice").asInt()).isEqualTo(70);

        data(member(post("/app/plan/swap"), Map.of("serviceDate", serviceDate.toString(),
                "mealType", "LUNCH", "dishVersionId", baseLunchVersionId)));
        JsonNode restored = data(member(get("/app/plan/day").param("date", serviceDate.toString())));
        assertThat(restored.path("dailyPrice").asInt()).isEqualTo(76);
        assertThat(restored.path("swapDifference").asDouble()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("替换目标含过敏原被拒绝")
    void swapBlockedByAllergen() throws Exception {
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        mockMvc.perform(post("/app/plan/swap/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("serviceDate", serviceDate.toString(),
                                "mealType", "LUNCH", "dishVersionId", peanutVersionId))))
                .andExpect(jsonPath("$.code").value(1206));
    }

    @Test
    @DisplayName("替换目标成本为 0 时拒绝，不得默认免费替换")
    void swapBlockedByMissingCost() throws Exception {
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        mockMvc.perform(post("/app/plan/swap/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("serviceDate", serviceDate.toString(),
                                "mealType", "LUNCH", "dishVersionId", zeroCostVersionId))))
                .andExpect(jsonPath("$.code").value(1208));
    }

    @Test
    @DisplayName("发布新定价版本后，已生成的计划仍按旧版本解释价格")
    void publishingNewVersionDoesNotRepriceExistingPlan() throws Exception {
        JsonNode before = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        String versionNo = before.path("pricingVersionNo").asText();

        long draftId = data(admin(post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "V9." + SEQ.incrementAndGet(),
                "dailyBasePrice", 70,
                "extraProteinPricePer10g", 1.90))).asLong();
        data(admin(post("/admin/meal/pricing/versions/" + draftId + "/review")));
        data(admin(post("/admin/meal/pricing/versions/" + draftId + "/publish")));

        JsonNode after = data(member(get("/app/plan/week").param("date", serviceDate.toString())));
        assertThat(after.path("pricingVersionNo").asText()).isEqualTo(versionNo);
        assertThat(findDay(after, serviceDate.toString()).path("dailyPrice").asInt()).isEqualTo(76);

        // 重新生成才使用新版本：70 + 1.90 × 53 ÷ 10 = 80.07 → 80
        JsonNode regenerated = data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        assertThat(regenerated.path("pricingVersionNo").asText()).isNotEqualTo(versionNo);
        assertThat(findDay(regenerated, serviceDate.toString()).path("dailyPrice").asInt()).isEqualTo(80);
    }

    // ==================== 数据准备 ====================

    /** 发布一个与 V1.0 参数一致的版本，保证每个用例都在已知基线价上验证。 */
    private void publishBaselinePricing() throws Exception {
        long id = data(admin(post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "PB-" + SEQ.incrementAndGet(),
                "dailyBasePrice", 66,
                "extraProteinPricePer10g", 1.90))).asLong();
        data(admin(post("/admin/meal/pricing/versions/" + id + "/review")));
        data(admin(post("/admin/meal/pricing/versions/" + id + "/publish")));
    }

    private void preparePublishedMenu() throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-P45-" + tag, "26.00", "0.90", null);
        long rice = ingredient("杂粮米-P45-" + tag, "8.00", "1.00", null);
        long peanut = ingredient("花生酱-P45-" + tag, "30.00", "1.00", "花生");
        long freebies = ingredient("零成本配菜-" + tag, "0.00", "1.00", null);

        long breakfast = dish("厚蛋烧-P45-" + tag, "早餐");
        long v1 = version(breakfast, 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long lunch = dish("鸡腿饭-P45-" + tag, "午餐");
        long v2 = version(lunch, 720, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        long dinner = dish("三文鱼-P45-" + tag, "晚餐");
        long v3 = version(dinner, 640, 700, 45,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 160),
                        Map.of("ingredientId", rice, "netWeightG", 120)));

        long altLunch = dish("香煎三文鱼饭-P45-" + tag, "午餐");
        altLunchVersionId = version(altLunch, 650, 720, 60,
                List.of(Map.of("ingredientId", rice, "netWeightG", 200)));

        long peanutDish = dish("花生酱拌面-P45-" + tag, "午餐");
        peanutVersionId = version(peanutDish, 600, 700, 40,
                List.of(Map.of("ingredientId", peanut, "netWeightG", 50)));

        long zeroDish = dish("零成本配菜-P45-" + tag, "午餐");
        zeroCostVersionId = version(zeroDish, 500, 500, 35,
                List.of(Map.of("ingredientId", freebies, "netWeightG", 100)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "P4.5 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-P45-" + tag))).asLong();
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
        body.put("trainingState", "每周三次力量训练");
        body.put("appetiteLevel", "NORMAL");
        body.put("dislikes", "香菜");
        body.put("allergies", "花生");
        body.put("proteinPref", "鸡胸肉,鱼");
        mockMvc.perform(put("/app/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
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

    private JsonNode findDay(JsonNode plan, String date) {
        for (JsonNode day : plan.path("days")) {
            if (day.path("serviceDate").asText().equals(date)) {
                return day;
            }
        }
        throw new AssertionError("未找到服务日 " + date);
    }

    private JsonNode findMeal(JsonNode dayOrPlan, String mealType) {
        JsonNode meals = dayOrPlan.has("days")
                ? findDay(dayOrPlan, serviceDate.toString()).path("meals")
                : dayOrPlan.path("meals");
        for (JsonNode meal : meals) {
            if (meal.path("mealType").asText().equals(mealType)) {
                return meal;
            }
        }
        throw new AssertionError("未找到餐次 " + mealType);
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
                .content(json(Map.of("code", code, "nickname", "测试会员"))), null)
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
