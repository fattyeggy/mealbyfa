package com.sancan.meal.feedback;

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
import java.nio.charset.StandardCharsets;
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

/** 完餐反馈与导出（手册 §5 收餐到反馈、§9 生产标签）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackAndExportApiTest {

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
    private LocalDate serviceDate;
    private long orderId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
        serviceDate = LocalDate.now().plusWeeks(2 + NEXT_WEEK.getAndIncrement()).with(DayOfWeek.WEDNESDAY);
        LocalDate weekStart = serviceDate.with(DayOfWeek.MONDAY);
        preparePublishedMenu(weekStart);

        String code = "feedback-" + SEQ.incrementAndGet();
        memberToken = memberToken(code);
        long userId = data(member(get("/app/me"))).path("id").asLong();
        onboard();
        data(member(post("/app/plan/generate"), Map.of("weekDate", weekStart.toString())));
        data(admin(post("/admin/meal/wallet/" + userId + "/credit"), Map.of("amount", 500, "reason", "反馈测试")));
        orderId = data(member(post("/app/orders"), Map.of(
                "serviceDate", serviceDate.toString(),
                "contactName", "张三", "contactPhone", "13800000000",
                "address", "新乡市示例路 1 号"))).path("id").asLong();
        data(admin(post("/admin/meal/orders/lock-run"), Map.of("serviceDate", serviceDate.toString())));
    }

    @Test
    @DisplayName("午餐批次送达后可反馈；未送达的晚餐批次被拒")
    void feedbackRequiresDelivery() throws Exception {
        // 未送达时：晚餐批次（BATCH_2）被拒
        mockMvc.perform(post("/app/orders/" + orderId + "/feedback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(feedbackBody("DINNER", "EATEN", 4))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1600));

        // 标记午餐批次送达后可反馈
        markDelivery("LUNCH", "DONE");
        JsonNode saved = data(member(post("/app/orders/" + orderId + "/feedback"),
                feedbackBody("LUNCH", "QUARTER", 5)));
        assertThat(saved.path("leftoverRatio").asText()).isEqualTo("QUARTER");
        assertThat(saved.path("satiety").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("同一餐次重复反馈被拒绝")
    void duplicatedFeedbackRejected() throws Exception {
        markDelivery("LUNCH", "DONE");
        data(member(post("/app/orders/" + orderId + "/feedback"), feedbackBody("LUNCH", "EATEN", 5)));

        mockMvc.perform(post("/app/orders/" + orderId + "/feedback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(feedbackBody("LUNCH", "EATEN", 5))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1601));
    }

    @Test
    @DisplayName("周复盘统计进食率与平均饱腹感")
    void weeklySummary() throws Exception {
        markDelivery("LUNCH", "DONE");
        markDelivery("DINNER", "DONE");

        data(member(post("/app/orders/" + orderId + "/feedback"), feedbackBody("LUNCH", "EATEN", 5)));
        data(member(post("/app/orders/" + orderId + "/feedback"), feedbackBody("DINNER", "HALF", 3)));

        JsonNode week = data(member(get("/app/feedback/week").param("date", serviceDate.toString())));
        assertThat(week.path("summary").path("totalMeals").asInt()).isEqualTo(2);
        // 吃完(1.0) + 剩一半(0.5) = 1.5 / 2 = 0.75
        assertThat(week.path("summary").path("eatenRatio").asDouble()).isEqualTo(0.75);
        assertThat(week.path("summary").path("avgSatiety").asDouble()).isEqualTo(4.0);
        assertThat(week.path("summary").path("lowSatietyCount").asInt()).isZero();
    }

    @Test
    @DisplayName("生产汇总、分装标签、配送清单均可导出 CSV")
    void exportsProduceCsv() throws Exception {
        data(admin(post("/admin/meal/kitchen/generate").param("serviceDate", serviceDate.toString())));

        String production = csv(admin(get("/admin/meal/kitchen/export/production")
                .param("serviceDate", serviceDate.toString())));
        assertThat(production).contains("服务日,配送批次,菜品,计划份数");
        assertThat(production).contains("鸡腿饭").contains(",1,0,0,PLANNED");

        String labels = csv(admin(get("/admin/meal/kitchen/export/labels")
                .param("serviceDate", serviceDate.toString())));
        assertThat(labels).contains("订单号,配送批次,餐次,菜品");
        assertThat(labels.lines().count()).isGreaterThanOrEqualTo(4); // 表头 + 3 餐

        String delivery = csv(admin(get("/admin/meal/orders/export/delivery")
                .param("serviceDate", serviceDate.toString())));
        assertThat(delivery).contains("订单号,配送批次,联系人");
        assertThat(delivery).contains("BATCH_1").contains("BATCH_2");

        // 带 UTF-8 BOM，Excel 打开中文不乱码
        MvcResult raw = mockMvc.perform(get("/admin/meal/orders/export/delivery")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("serviceDate", serviceDate.toString())).andReturn();
        byte[] bytes = raw.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }


    /** 按餐次找到对应配送记录，再按 deliveryId 更新单条。 */
    private void markDelivery(String slot, String status) throws Exception {
        JsonNode detail = data(admin(get("/admin/meal/orders/" + orderId)));
        long deliveryId = -1;
        for (JsonNode d : detail.path("deliveries")) {
            if (slot.equals(d.path("deliverySlot").asText())) {
                deliveryId = d.path("id").asLong();
            }
        }
        assertThat(deliveryId).as("未找到 " + slot + " 配送记录").isPositive();
        data(admin(post("/admin/meal/orders/" + orderId + "/delivery"),
                Map.of("deliveryId", deliveryId, "status", status)));
    }

    // ==================== helpers ====================

    private String csv(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains("csv");
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private Map<String, Object> feedbackBody(String mealType, String ratio, int satiety) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mealType", mealType);
        body.put("leftoverRatio", ratio);
        body.put("satiety", satiety);
        body.put("trainingState", "力量训练");
        body.put("content", "口味偏咸");
        return body;
    }

    private void preparePublishedMenu(LocalDate weekStart) throws Exception {
        int tag = SEQ.incrementAndGet();
        long chicken = ingredient("鸡胸肉-P8-" + tag, "26.00");
        long rice = ingredient("杂粮米-P8-" + tag, "8.00");
        long v1 = version(dish("厚蛋烧-P8-" + tag, "早餐"), 320, 420, 28,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 120)));
        long v2 = version(dish("鸡腿饭-P8-" + tag, "午餐"), 720, 760, 42,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 150),
                        Map.of("ingredientId", rice, "netWeightG", 180)));
        long v3 = version(dish("三文鱼-P8-" + tag, "晚餐"), 640, 700, 45,
                List.of(Map.of("ingredientId", chicken, "netWeightG", 160),
                        Map.of("ingredientId", rice, "netWeightG", 120)));

        long menu = data(admin(post("/admin/meal/menus"), Map.of(
                "themeName", "P8 测试周-" + tag, "region", "新乡",
                "startDate", weekStart.toString(), "version", "V1.0-P8-" + tag))).asLong();
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

    private void onboard() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gender", "MALE");
        body.put("birthDate", LocalDate.now().minusYears(30).toString());
        body.put("heightCm", 175);
        body.put("weightKg", 75);
        body.put("activityLevel", "MODERATE");
        body.put("goal", "MAINTAIN");
        body.put("appetiteLevel", "NORMAL");
        body.put("allergies", "");
        data(member(put("/app/profile"), body));
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
                .content(json(Map.of("code", code, "nickname", "反馈测试"))), null)
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
