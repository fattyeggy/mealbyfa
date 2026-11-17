package com.sancan.meal.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
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

/** 业务要求：菜单总热量高不能被阻止发布，也不能因此生成不了计划。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HighCalorieMenuTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("高热量菜单发布为人工确认，增肌用户仍能生成计划")
    void highCalorieMenuStillPublishesAndPlans() throws Exception {
        String admin = adminToken();
        LocalDate weekStart = LocalDate.now().plusWeeks(40).with(DayOfWeek.MONDAY);
        LocalDate day = weekStart.plusDays(2);
        int tag = SEQ.incrementAndGet();

        Map<String, Object> ingredientBody = new LinkedHashMap<>();
        ingredientBody.put("name", "高热量主料-" + tag);
        ingredientBody.put("category", "组件");
        ingredientBody.put("purchaseUnit", "kg");
        ingredientBody.put("usageUnit", "kg");
        ingredientBody.put("conversionRate", 1);
        ingredientBody.put("edibleRate", new BigDecimal("0.95"));
        ingredientBody.put("movingAvgPrice", 30);
        ingredientBody.put("componentType", "PROTEIN");
        ingredientBody.put("kcalPer100g", 200);
        ingredientBody.put("proteinPer100g", 30);
        ingredientBody.put("carbPer100g", 20);
        ingredientBody.put("fatPer100g", 10);
        ingredientBody.put("status", 0);
        long ingredient = data(admin(post("/admin/meal/ingredients"), ingredientBody)).asLong();

        List<Long> versions = new ArrayList<>();
        for (String meal : List.of("早餐", "午餐", "晚餐")) {
            long dish = data(admin(post("/admin/meal/dishes"), Map.of(
                    "name", "高热量" + meal + "-" + tag, "category", meal,
                    "description", "测试", "storageMethod", "冷藏", "reheatMethod", "微波", "status", 0))).asLong();
            long version = data(admin(post("/admin/meal/dishes/" + dish + "/versions"), Map.of(
                    "netWeightG", 1000, "kcal", 1200, "proteinG", 200, "carbG", 300, "fatG", 40,
                    "bom", List.of(Map.of("ingredientId", ingredient, "netWeightG", 200))))).asLong();
            versions.add(version);
        }

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "高热量周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V-HC-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", versions.get(0)));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", versions.get(1)));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", versions.get(2)));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));

        // 发布不应该因为热量/重量/蛋白超上限而失败
        data(admin(post("/admin/meal/menus/" + menu + "/publish"), null));

        JsonNode check = data(admin(get("/admin/meal/menus/" + menu + "/check")));
        JsonNode nutrition = null;
        for (JsonNode item : check.path("items")) {
            if ("营养与全天重量在允许范围".equals(item.path("name").asText())) {
                nutrition = item;
            }
        }
        assertThat(nutrition).isNotNull();
        assertThat(nutrition.path("status").asText()).isEqualTo("MANUAL");
        assertThat(nutrition.path("detail").asText()).contains("不阻止发布");

        // 增肌用户仍然能生成计划
        String member = memberToken("hc-" + tag);
        Map<String, Object> onboarding = new LinkedHashMap<>();
        onboarding.put("goal", "bulk");
        onboarding.put("sex", "male");
        onboarding.put("age", 24);
        onboarding.put("heightCm", 180);
        onboarding.put("weightKg", 68);
        onboarding.put("bodyFatPct", null);
        onboarding.put("dailyActivity", "light");
        onboarding.put("workoutsPerWeek", 5);
        onboarding.put("workoutMinutes", 60);
        onboarding.put("workoutType", "strength");
        onboarding.put("workoutIntensity", "high");
        onboarding.put("healthFlags", List.of("none"));
        onboarding.put("allergies", List.of());
        onboarding.put("dietRestrictions", List.of());
        onboarding.put("orderedMeals", List.of("breakfast", "lunch", "dinner"));
        onboarding.put("privacyAccepted", true);
        data(member(post("/app/onboarding"), onboarding, member));
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), member));
        JsonNode planDay = data(member(get("/app/plan/day").param("date", day.toString()), member));
        assertThat(planDay.path("meals")).hasSize(3);
    }

    // ==================== helpers ====================

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
        return call(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "admin123")))).path("data").path("token").asText();
    }

    private String memberToken(String code) throws Exception {
        return call(post("/app/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "nickname", "高热量测试")))).path("data").path("token").asText();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) throws Exception {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken());
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        MockHttpServletRequestBuilder b = admin(builder);
        if (body != null) {
            b = b.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return b;
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder builder, Object body, String token) throws Exception {
        return member(builder, token).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode data(MockHttpServletRequestBuilder builder) throws Exception {
        JsonNode root = call(builder);
        assertThat(root.path("code").asInt()).as("接口返回异常: " + root).isZero();
        return root.path("data");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
