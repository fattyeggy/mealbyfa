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

/** 开发说明第 9–10 节：建档接口、拦截、计划落库与每日缺口。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingApiTest {

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
    private String memberToken;
    private LocalDate day;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        day = LocalDate.now().plusWeeks(25 + WEEK.getAndIncrement()).with(DayOfWeek.WEDNESDAY);
        weekStart = day.with(DayOfWeek.MONDAY);
        prepareMenu();
        memberToken = memberToken("onb-" + SEQ.incrementAndGet());
    }

    @Test
    @DisplayName("用例1：建档返回 BMR/AF/TDEE/热量/档位/宏量，并落库")
    void onboardingCase1() throws Exception {
        JsonNode resp = data(member(post("/app/onboarding"), case1Body()));
        assertThat(resp.path("status").asText()).isEqualTo("ok");
        JsonNode plan = resp.path("plan");
        assertThat(plan.path("bmr").asDouble()).isCloseTo(1699, org.assertj.core.data.Offset.offset(1.0));
        assertThat(plan.path("activityFactor").asDouble()).isEqualTo(1.55);
        assertThat(plan.path("tdee").asDouble()).isCloseTo(2633, org.assertj.core.data.Offset.offset(1.0));
        assertThat(plan.path("kcal").asInt()).isEqualTo(2110);
        assertThat(plan.path("proteinRaw").asInt()).isEqualTo(150);
        assertThat(plan.path("proteinTier").asInt()).isEqualTo(160);
        assertThat(plan.path("fatG").asInt()).isEqualTo(60);
        assertThat(plan.path("carbG").asInt()).isEqualTo(232);

        JsonNode profile = data(member(get("/app/profile")));
        assertThat(profile.path("onboardingCompleted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("拦截：孕期返回 blocked + manual_consult，不返回方案")
    void blockedByPregnancy() throws Exception {
        Map<String, Object> body = case1Body();
        body.put("sex", "female");
        body.put("healthFlags", List.of("pregnant"));
        JsonNode resp = data(member(post("/app/onboarding"), body));
        assertThat(resp.path("status").asText()).isEqualTo("blocked");
        assertThat(resp.path("reason").asText()).isEqualTo("pregnant");
        assertThat(resp.path("action").asText()).isEqualTo("manual_consult");
        assertThat(resp.path("plan").isMissingNode() || resp.path("plan").isNull()).isTrue();
    }

    @Test
    @DisplayName("建档后生成计划使用蛋白档位，缺口按已订餐次计算")
    void planUsesTierAndGap() throws Exception {
        data(member(post("/app/onboarding"), case1Body()));
        // 只订午晚餐
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        JsonNode gap = data(member(get("/app/onboarding/gap").param("date", day.toString())));
        assertThat(gap.path("targetKcal").asInt()).isEqualTo(2110);
        JsonNode protein = null;
        for (JsonNode line : gap.path("lines")) {
            if ("protein".equals(line.path("nutrient").asText())) {
                protein = line;
            }
        }
        assertThat(protein).isNotNull();
        assertThat(protein.path("targetG").asInt()).isEqualTo(160);
        assertThat(protein.path("actualG").asInt()).isGreaterThan(0);
        assertThat(protein.path("achieved").asBoolean()).isFalse();
        assertThat(protein.path("volumeText").asText()).contains("约");
    }

    // ==================== fixture ====================

    private Map<String, Object> case1Body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goal", "cut");
        body.put("sex", "male");
        body.put("age", 30);
        body.put("heightCm", 175);
        body.put("weightKg", 75);
        body.put("bodyFatPct", null);
        body.put("dailyActivity", "sedentary");
        body.put("workoutsPerWeek", 4);
        body.put("workoutMinutes", 60);
        body.put("workoutType", "strength");
        body.put("workoutIntensity", "medium");
        body.put("healthFlags", List.of("none"));
        body.put("allergies", List.of());
        body.put("dietRestrictions", List.of());
        body.put("orderedMeals", List.of("lunch", "dinner"));
        body.put("tastePrefs", Map.of("spice", 1, "staple", "rice"));
        body.put("privacyAccepted", true);
        return body;
    }

    private void prepareMenu() throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-ONB-" + tag);
        long rice = ingredient("杂粮米-ONB-" + tag);
        long v1 = version(dish("厚蛋烧-ONB-" + tag, "早餐"), 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long v2 = version(dish("鸡腿饭-ONB-" + tag, "午餐"), 620, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        long v3 = version(dish("三文鱼-ONB-" + tag, "晚餐"), 640, 700, 45,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 160),
                        Map.of("ingredientId", rice, "netWeightG", 120)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "ONB 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-ONB-" + tag))).asLong();
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

    private long ingredient(String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("category", "主料");
        body.put("purchaseUnit", "kg");
        body.put("usageUnit", "kg");
        body.put("conversionRate", 1);
        body.put("edibleRate", new BigDecimal("0.90"));
        body.put("movingAvgPrice", new BigDecimal("26.00"));
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
                .content(json(Map.of("code", code, "nickname", "建档测试"))), null)
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
