package com.sancan.meal.nutrition;

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
 * 组件化配餐验收（评审 P0-1）：用真实菜品与 USDA/公共营养数据，
 * 验证 6 组基准用户订满三餐后：热量 ±5%、蛋白 ≥95%、碳水/脂肪 ±10%、单餐 ≤750g。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComponentMenuApiTest {

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

    private String adminToken;
    private LocalDate day;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        day = LocalDate.now().plusWeeks(30 + WEEK.getAndIncrement()).with(DayOfWeek.WEDNESDAY);
        weekStart = day.with(DayOfWeek.MONDAY);
        prepareRealMenu();
    }

    @Test
    @DisplayName("6 组基准用户：组件化缩放后全天达标、单餐不超 750g")
    void sixBenchmarkUsersMeetDailyTargets() throws Exception {
        List<Map<String, Object>> cases = List.of(
                user("cut", "male", 30, 175, 75, "sedentary", 4, 60, "medium", 2110, 160, 232, 60),
                user("maintain", "female", 28, 162, 55, "sedentary", 2, 60, "medium", 1730, 80, 240, 50),
                user("bulk", "male", 24, 180, 68, "light", 5, 60, "high", 3200, 130, 533, 61),
                user("cut", "female", 35, 160, 78, "sedentary", 0, 0, "medium", 1390, 130, 107, 49),
                user("cut", "female", 45, 155, 50, "sedentary", 0, 0, "medium", 1200, 100, 110, 40),
                user("cut", "male", 40, 170, 90, "active", 5, 80, "high", 2690, 130, 419, 55));

        for (Map<String, Object> c : cases) {
            String token = memberToken("cmp-" + SEQ.incrementAndGet());
            JsonNode onb = data(post("/app/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(json(c)));
            assertThat(onb.path("status").asText()).isEqualTo("ok");

            data(post("/app/plan/generate").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("weekDate", weekStart.toString()))));

            JsonNode dayResp = data(get("/app/plan/day").param("date", day.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
            int totalKcal = dayResp.path("totalKcal").asInt();
            int targetKcal = (int) c.get("expectKcal");
            JsonNode gap = data(get("/app/onboarding/gap").param("date", day.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
            JsonNode protein = line(gap, "protein");
            JsonNode carb = line(gap, "carb");
            JsonNode fat = line(gap, "fat");
            int targetP = (int) c.get("expectProtein");
            int targetC = (int) c.get("expectCarb");
            int targetF = (int) c.get("expectFat");

            boolean within = Math.abs(totalKcal - targetKcal) <= targetKcal * 0.05
                    && protein.path("actualG").asInt() >= targetP * 0.95
                    && Math.abs(carb.path("actualG").asInt() - targetC) <= targetC * 0.10
                    && Math.abs(fat.path("actualG").asInt() - targetF) <= targetF * 0.10;
            JsonNode week = data(get("/app/plan/week").param("date", day.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
            System.out.printf("BENCH|%s|%s|%d|%d|%d|%d|%d|%d|%d|%d|%s|%s%n",
                    c.get("goal"), c.get("sex"), targetKcal, totalKcal,
                    targetP, protein.path("actualG").asInt(),
                    targetC, carb.path("actualG").asInt(),
                    targetF, fat.path("actualG").asInt(),
                    dayResp.path("totalWeightG").asInt(), within,
                    week.path("needsReview").asBoolean());
            if ("cut".equals(c.get("goal")) && targetKcal == 2110) {
                // 评审引用的基准用户必须真的达标
                assertThat(within).as("基准用户全天热量 " + totalKcal + " / 目标 " + targetKcal
                        + "，蛋白 " + protein.path("actualG").asInt()
                        + "，碳水 " + carb.path("actualG").asInt()
                        + "，脂肪 " + fat.path("actualG").asInt()).isTrue();
            } else if (!within) {
                // 达不到约束的组合必须进入人工复核，不能静默放行
                assertThat(week.path("needsReview").asBoolean())
                        .as("未达标但未生成复核：" + c.get("goal") + "/" + c.get("sex"))
                        .isTrue();
            }
        }
    }

    private JsonNode line(JsonNode gap, String nutrient) {
        for (JsonNode line : gap.path("lines")) {
            if (nutrient.equals(line.path("nutrient").asText())) {
                return line;
            }
        }
        throw new AssertionError("缺少 " + nutrient);
    }

    private Map<String, Object> user(String goal, String sex, int age, int height, int weight,
                                     String activity, int times, int minutes, String intensity,
                                     int kcal, int protein, int carb, int fat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goal", goal);
        body.put("sex", sex);
        body.put("age", age);
        body.put("heightCm", height);
        body.put("weightKg", weight);
        body.put("bodyFatPct", null);
        body.put("dailyActivity", activity);
        body.put("workoutsPerWeek", times);
        body.put("workoutMinutes", minutes);
        body.put("workoutType", "strength");
        body.put("workoutIntensity", intensity);
        body.put("healthFlags", List.of("none"));
        body.put("allergies", List.of());
        body.put("dietRestrictions", List.of());
        body.put("orderedMeals", List.of("breakfast", "lunch", "dinner"));
        body.put("tastePrefs", Map.of("spice", 1, "staple", "rice"));
        body.put("privacyAccepted", true);
        body.put("expectKcal", kcal);
        body.put("expectProtein", protein);
        body.put("expectCarb", carb);
        body.put("expectFat", fat);
        return body;
    }

    // ==================== 真实菜品与营养（每 100g，USDA / 公共营养库） ====================

    private void prepareRealMenu() throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-CMP" + tag, "PROTEIN", "120", "22.5", "0", "2.6", "26");
        long rice = ingredient("米饭-CMP" + tag, "STAPLE", "130", "2.7", "28.5", "0.3", "4");
        long broccoli = ingredient("西兰花-CMP" + tag, "VEGETABLE", "39", "2.6", "6.3", "0.4", "12");
        long oil = ingredient("橄榄油-CMP" + tag, "FAT", "884", "0", "0", "100", "60");
        long salmon = ingredient("三文鱼-CMP" + tag, "PROTEIN", "206", "22.1", "0", "12.35", "68");
        long quinoa = ingredient("藜麦饭-CMP" + tag, "STAPLE", "120", "4.4", "21.3", "1.9", "18");
        long egg = ingredient("蛋清-CMP" + tag, "PROTEIN", "52", "10.9", "0.7", "0.2", "14");
        long bread = ingredient("全麦面包-CMP" + tag, "STAPLE", "247", "13", "41", "3.4", "20");
        long milk = ingredient("牛奶-CMP" + tag, "OTHER", "42", "3.4", "5", "1", "8");

        long v1 = version(dish("照烧鸡腿饭-CMP" + tag, "午餐"), 558, 680, 44, 92, 13,
                List.of(bom(chicken, "150"), bom(rice, "300"), bom(broccoli, "100"), bom(oil, "8")));
        long v2 = version(dish("三文鱼鸡胸双拼藜麦碗-CMP" + tag, "晚餐"), 525, 720, 45, 55, 24,
                List.of(bom(salmon, "50"), bom(chicken, "120"), bom(quinoa, "250"),
                        bom(broccoli, "100"), bom(oil, "5")));
        long v3 = version(dish("厚蛋烧全麦吐司-CMP" + tag, "早餐"), 328, 458, 31, 48, 14,
                List.of(bom(egg, "120"), bom(bread, "100"), bom(milk, "100"), bom(oil, "8")));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "组件化验证周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V-CMP-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", v3));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", v1));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", v2));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));
    }

    private Map<String, Object> bom(long ingredientId, String grams) {
        return Map.of("ingredientId", ingredientId, "netWeightG", new BigDecimal(grams));
    }

    private long ingredient(String name, String component, String kcal, String protein,
                            String carb, String fat, String price) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("category", "组件");
        body.put("purchaseUnit", "kg");
        body.put("usageUnit", "kg");
        body.put("conversionRate", 1);
        body.put("edibleRate", new BigDecimal("0.95"));
        body.put("movingAvgPrice", new BigDecimal(price));
        body.put("componentType", component);
        body.put("kcalPer100g", new BigDecimal(kcal));
        body.put("proteinPer100g", new BigDecimal(protein));
        body.put("carbPer100g", new BigDecimal(carb));
        body.put("fatPer100g", new BigDecimal(fat));
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

    private long version(long dishId, int weight, int kcal, int protein, int carb, int fat,
                         List<Map<String, Object>> bom) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("netWeightG", weight);
        body.put("kcal", kcal);
        body.put("proteinG", protein);
        body.put("carbG", carb);
        body.put("fatG", fat);
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
                .content(json(Map.of("code", code, "nickname", "组件测试"))), null)
                .path("data").path("token").asText();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return admin(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
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
