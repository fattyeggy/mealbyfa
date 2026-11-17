package com.sancan.meal.menu;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 菜单域接口测试：建档 → 配置 21 餐次 → 发布前检查 → 发布 → 冻结，
 * 以及日期校验、版本唯一、复制菜单与公共菜单读取。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuDomainApiTest {

    // 使用远期固定日期，避免与其他用例（按当前日期推算服务周）互相干扰
    private static final LocalDate WEEK_START = LocalDate.of(2030, 1, 7); // 周一

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private int seq;

    @BeforeEach
    void setUp() throws Exception {
        if (adminUserMapper.selectByUsername("admin") == null) {
            AdminUserDO user = new AdminUserDO();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setNickname("超级管理员");
            user.setRole("SUPER_ADMIN");
            user.setStatus(0);
            adminUserMapper.insert(user);
        }
        JsonNode login = call(post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "admin123"))));
        token = login.path("data").path("token").asText();
    }

    @Test
    @DisplayName("完整链路：配置 21 餐次 → 检查通过 → 发布 → 不可原地修改 → 公共菜单可见")
    void publishFlowAndImmutability() throws Exception {
        long ingredientId = createIngredient("鸡胸肉");
        long dishId = createDish("日式厚蛋鸡肉三明治");
        long versionId = createVersion(dishId, ingredientId);
        long menuId = createMenu("东京主题周", WEEK_START, "V1.0-A");
        upsertFullWeek(menuId, versionId, WEEK_START);

        JsonNode check = data(get("/admin/meal/menus/" + menuId + "/check"));
        assertThat(check.path("passed").asBoolean()).isTrue();
        assertThat(check.path("items")).hasSize(6);
        assertThat(check.path("items").get(0).path("status").asText()).isEqualTo("PASS");
        // 单份成本 4.3333 元，21 个餐次合计 90.9993 元
        assertThat(new BigDecimal(check.path("standardCost").asText()))
                .isEqualByComparingTo(new BigDecimal("90.9993"));

        JsonNode detail = data(get("/admin/meal/menus/" + menuId));
        assertThat(detail.path("filledMeals").asInt()).isEqualTo(21);
        assertThat(detail.path("days")).hasSize(7);

        mockMvc.perform(post("/admin/meal/menus/" + menuId + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        // 发布后不可原地修改
        mockMvc.perform(put("/admin/meal/menus/" + menuId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("themeName", "改名"))))
                .andExpect(jsonPath("$.code").value(1104));

        mockMvc.perform(put("/admin/meal/menus/" + menuId + "/meals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("meals", List.of()))))
                .andExpect(jsonPath("$.code").value(1104));

        // 公共菜单可见，且返回 21 个餐次
        JsonNode publicWeek = data(get("/app/menu/week").param("date", "2030-01-09"));
        assertThat(publicWeek.path("published").asBoolean()).isTrue();
        assertThat(publicWeek.path("menuId").asLong()).isEqualTo(menuId);
        int meals = 0;
        for (JsonNode day : publicWeek.path("days")) {
            meals += day.path("meals").size();
        }
        assertThat(meals).isEqualTo(21);
        // 未注册视角不出现价格字段
        assertThat(publicWeek.toString()).doesNotContain("price").doesNotContain("cost");
    }

    @Test
    @DisplayName("开始日期不是周一，拒绝创建")
    void startDateMustBeMonday() throws Exception {
        mockMvc.perform(post("/admin/meal/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("themeName", "错误周", "region", "新乡",
                                "startDate", "2030-01-08", "version", "V1.0-B"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("开始日期必须是周一"));
    }

    @Test
    @DisplayName("同一开始日的相同版本号不可重复创建")
    void duplicateVersionRejected() throws Exception {
        createMenu("重复周", WEEK_START, "V1.0-C");
        mockMvc.perform(post("/admin/meal/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("themeName", "重复周2", "region", "新乡",
                                "startDate", WEEK_START.toString(), "version", "V1.0-C"))))
                .andExpect(jsonPath("$.code").value(1103));
    }

    @Test
    @DisplayName("餐次不完整时检查不通过，且发布被拒绝")
    void checkFailsWhenMealsMissing() throws Exception {
        long ingredientId = createIngredient("三文鱼");
        long dishId = createDish("味噌三文鱼");
        long versionId = createVersion(dishId, ingredientId);
        long menuId = createMenu("未完成周", WEEK_START, "V1.0-D");

        upsertMeals(menuId, List.of(Map.of("serviceDate", WEEK_START.toString(),
                "mealType", "BREAKFAST", "dishVersionId", versionId)));

        JsonNode check = data(get("/admin/meal/menus/" + menuId + "/check"));
        assertThat(check.path("passed").asBoolean()).isFalse();
        JsonNode first = check.path("items").get(0);
        assertThat(first.path("status").asText()).isEqualTo("FAIL");
        assertThat(first.path("detail").asText()).contains("缺少");

        mockMvc.perform(post("/admin/meal/menus/" + menuId + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(1102))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("21 个餐次配置完整")));
    }

    @Test
    @DisplayName("复制菜单会把服务日整体后移，并保持 21 个餐次")
    void copyMenuShiftsDates() throws Exception {
        long ingredientId = createIngredient("牛里脊");
        long dishId = createDish("照烧鸡腿杂粮饭");
        long versionId = createVersion(dishId, ingredientId);
        long menuId = createMenu("复制源周", WEEK_START, "V1.0-E");
        upsertFullWeek(menuId, versionId, WEEK_START);

        JsonNode copied = data(post("/admin/meal/menus/" + menuId + "/copy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("startDate", WEEK_START.plusDays(7).toString(), "version", "V1.0-E2"))));
        long newId = copied.asLong();

        JsonNode detail = data(get("/admin/meal/menus/" + newId));
        assertThat(detail.path("startDate").asText()).isEqualTo(WEEK_START.plusDays(7).toString());
        assertThat(detail.path("status").asText()).isEqualTo("DRAFT");
        assertThat(detail.path("filledMeals").asInt()).isEqualTo(21);
        assertThat(detail.path("days").get(0).path("serviceDate").asText())
                .isEqualTo(WEEK_START.plusDays(7).toString());
    }

    @Test
    @DisplayName("未发布的周返回 published=false 与预计发布时间")
    void unpublishedWeekHasExpectedPublishTime() throws Exception {
        long ingredientId = createIngredient("羽衣甘蓝");
        long dishId = createDish("羽衣甘蓝沙拉");
        long versionId = createVersion(dishId, ingredientId);
        long menuId = createMenu("草稿周", LocalDate.of(2030, 1, 21), "V1.0-F");
        upsertFullWeek(menuId, versionId, LocalDate.of(2030, 1, 21));
        // 触发一次读取，确认草稿不对外可见为已发布
        JsonNode publicWeek = data(get("/app/menu/week").param("date", "2030-01-23"));
        assertThat(publicWeek.path("published").asBoolean()).isFalse();
        assertThat(publicWeek.path("expectedPublishTime").asText()).isNotBlank();
    }

    // ==================== helpers ====================

    private long createIngredient(String name) throws Exception {
        String unique = name + "-" + (++seq);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", unique);
        body.put("category", "蛋白");
        body.put("purchaseUnit", "kg");
        body.put("usageUnit", "kg");
        body.put("conversionRate", 1);
        body.put("edibleRate", new BigDecimal("0.9"));
        body.put("movingAvgPrice", new BigDecimal("26.00"));
        body.put("status", 0);
        return data(post("/admin/meal/ingredients")
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).asLong();
    }

    private long createDish(String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name + "-" + (++seq));
        body.put("category", "热菜");
        body.put("description", "测试菜品");
        body.put("storageMethod", "0-4℃ 冷藏");
        body.put("reheatMethod", "微波 90 秒");
        body.put("status", 0);
        return data(post("/admin/meal/dishes")
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).asLong();
    }

    private long createVersion(long dishId, long ingredientId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("netWeightG", 500);
        body.put("kcal", 550);
        body.put("proteinG", 30);
        body.put("carbG", 60);
        body.put("fatG", 15);
        body.put("bom", List.of(Map.of("ingredientId", ingredientId, "netWeightG", 150)));
        return data(post("/admin/meal/dishes/" + dishId + "/versions")
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).asLong();
    }

    private long createMenu(String theme, LocalDate start, String version) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("themeName", theme);
        body.put("region", "新乡");
        body.put("startDate", start.toString());
        body.put("version", version);
        return data(post("/admin/meal/menus")
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).asLong();
    }

    private void upsertFullWeek(long menuId, long versionId, LocalDate start) throws Exception {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            for (String type : List.of("BREAKFAST", "LUNCH", "DINNER")) {
                slots.add(Map.of("serviceDate", start.plusDays(i).toString(),
                        "mealType", type, "dishVersionId", versionId));
            }
        }
        upsertMeals(menuId, slots);
    }

    private void upsertMeals(long menuId, List<Map<String, Object>> slots) throws Exception {
        data(put("/admin/meal/menus/" + menuId + "/meals")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("meals", slots))));
    }

    private JsonNode call(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
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
