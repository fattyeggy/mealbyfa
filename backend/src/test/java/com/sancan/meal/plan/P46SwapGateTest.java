package com.sancan.meal.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
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
 * R08：生产开关 {@code meal.pricing.allow-unreviewed-swap=false} 时，
 * 未审核的替换选项必须被拒绝（计价规则 V1.0 §6.4）。
 */
@SpringBootTest(properties = "meal.pricing.allow-unreviewed-swap=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P46SwapGateTest {

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
    @Autowired
    private DishVersionMapper dishVersionMapper;

    private String adminToken;
    private String memberToken;
    private LocalDate weekStart;
    private LocalDate serviceDate;
    /** 已审核的替换目标。 */
    private long altLunchVersionId;
    /** 未审核的替换目标。 */
    private long strangerVersionId;
    private long lunchVersionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        weekStart = LocalDate.now().plusWeeks(20 + NEXT_WEEK.getAndIncrement()).with(java.time.DayOfWeek.MONDAY);
        serviceDate = weekStart.plusDays(2);
        preparePublishedMenu();
        memberToken = memberToken("swapgate-" + SEQ.incrementAndGet());
        onboard();
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
    }

    @Test
    @DisplayName("R08 未审核替换在开关关闭时被拒绝")
    void unreviewedSwapRejected() throws Exception {
        mockMvc.perform(post("/app/plan/swap/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("serviceDate", serviceDate.toString(),
                                "mealType", "LUNCH", "dishVersionId", strangerVersionId))))
                .andExpect(jsonPath("$.code").value(1209));

        mockMvc.perform(post("/app/plan/swap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("serviceDate", serviceDate.toString(),
                                "mealType", "LUNCH", "dishVersionId", strangerVersionId))))
                .andExpect(jsonPath("$.code").value(1209));
    }

    @Test
    @DisplayName("R09 已审核选项使用冻结成本：成本被重算后差价不变")
    void approvedSwapUsesFrozenCost() throws Exception {
        JsonNode before = previewSwap(altLunchVersionId);
        BigDecimal diffBefore = new BigDecimal(before.path("swapDifference").asText());
        int dailyBefore = before.path("after").path("dailyPrice").asInt();

        // 模拟成本重算：直接把替换菜品的标准份成本改成 3 倍
        DishVersionDO version = dishVersionMapper.selectById(altLunchVersionId);
        BigDecimal originalCost = version.getCost();
        version.setCost(originalCost.multiply(BigDecimal.valueOf(3)));
        dishVersionMapper.updateById(version);

        JsonNode after = previewSwap(altLunchVersionId);
        assertThat(new BigDecimal(after.path("swapDifference").asText()))
                .as("冻结成本后差价不应随成本重算变化")
                .isEqualByComparingTo(diffBefore);
        assertThat(after.path("after").path("dailyPrice").asInt()).isEqualTo(dailyBefore);
    }

    @Test
    @DisplayName("R10 同一替换选项对不同蛋白目标产生不同差价，不滥用固定 costDelta")
    void differentProteinTargetsGetDifferentDifference() throws Exception {
        BigDecimal diff75 = new BigDecimal(previewSwap(altLunchVersionId).path("swapDifference").asText());

        // 另一位 60kg 用户，目标蛋白更低
        String otherToken = memberToken("swapgate-60kg-" + SEQ.incrementAndGet());
        onboardWith(otherToken, 60);
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString()), otherToken));
        JsonNode preview = data(member(post("/app/plan/swap/preview"), Map.of(
                "serviceDate", serviceDate.toString(),
                "mealType", "LUNCH",
                "dishVersionId", altLunchVersionId), otherToken));
        BigDecimal diff60 = new BigDecimal(preview.path("swapDifference").asText());

        assertThat(diff60).as("不同蛋白目标应按个人份量重算，差价不同")
                .isNotEqualByComparingTo(diff75);
    }

    private JsonNode previewSwap(long targetVersionId) throws Exception {
        return data(member(post("/app/plan/swap/preview"), Map.of(
                "serviceDate", serviceDate.toString(),
                "mealType", "LUNCH",
                "dishVersionId", targetVersionId)));
    }

    // ==================== 数据准备 ====================

    private void preparePublishedMenu() throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-R08-" + tag, "26.00");
        long rice = ingredient("杂粮米-R08-" + tag, "8.00");
        long v1 = version(dish("厚蛋烧-R08-" + tag, "早餐"), 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long v2 = version(dish("鸡腿饭-R08-" + tag, "午餐"), 720, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        lunchVersionId = v2;
        long v3 = version(dish("三文鱼-R08-" + tag, "晚餐"), 640, 700, 45,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 160),
                        Map.of("ingredientId", rice, "netWeightG", 120)));
        altLunchVersionId = version(dish("替代饭-R08-" + tag, "午餐"), 650, 720, 60,
                List.of(Map.of("ingredientId", rice, "netWeightG", 200)));
        strangerVersionId = version(dish("未审核替代-R08-" + tag, "午餐"), 700, 730, 50,
                List.of(Map.of("ingredientId", rice, "netWeightG", 220)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "R08 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-R08-" + tag))).asLong();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            slots.add(Map.of("serviceDate", date, "mealType", "BREAKFAST", "dishVersionId", v1));
            slots.add(Map.of("serviceDate", date, "mealType", "LUNCH", "dishVersionId", v2));
            slots.add(Map.of("serviceDate", date, "mealType", "DINNER", "dishVersionId", v3));
        }
        data(admin(put("/admin/meal/menus/" + menu + "/meals"), Map.of("meals", slots)));
        data(admin(post("/admin/meal/menus/" + menu + "/publish")));

        long versionId = data(admin(post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "PB8-" + SEQ.incrementAndGet(),
                "dailyBasePrice", 66,
                "extraProteinPricePer10g", 1.90))).asLong();
        data(admin(post("/admin/meal/pricing/versions/" + versionId + "/swaps"), Map.of(
                "mealType", "LUNCH",
                "fromDishVersionId", lunchVersionId,
                "toDishVersionId", altLunchVersionId,
                "remark", "R09 审核替换")));
        data(admin(post("/admin/meal/pricing/versions/" + versionId + "/review")));
        data(admin(post("/admin/meal/pricing/versions/" + versionId + "/publish")));
    }

    private void onboard() throws Exception {
        onboardWith(memberToken, 75);
    }

    private void onboardWith(String token, int weightKg) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gender", "MALE");
        body.put("birthDate", LocalDate.now().minusYears(30).toString());
        body.put("heightCm", 175);
        body.put("weightKg", weightKg);
        body.put("activityLevel", "MODERATE");
        body.put("goal", "MAINTAIN");
        body.put("appetiteLevel", "NORMAL");
        body.put("allergies", "");
        dataAs(put("/app/profile").contentType(MediaType.APPLICATION_JSON).content(json(body)), token);
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
                .content(json(Map.of("code", code, "nickname", "R08"))), null)
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
