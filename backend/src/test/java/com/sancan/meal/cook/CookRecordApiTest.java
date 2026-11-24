package com.sancan.meal.cook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.plan.service.ComponentPortioner;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 做菜记录：记录 → 计算 → 照片 → 同步成菜品版本 → 系统配餐时汤汁跟主料缩放。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CookRecordApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

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
    @Autowired
    private ComponentPortioner componentPortioner;

    private String token;
    private final Map<String, Long> ids = new LinkedHashMap<>();

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
        token = call(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "admin123"))))
                .path("data").path("token").asText();
        int n = SEQ.incrementAndGet();
        // 食材库里故意把油标成 FAT、酱油标成 OTHER：同步后以配方行的组件类型为准
        ids.put("chicken", ingredient("做菜鸡胸" + n, "PROTEIN", "120", "22.5", "0", "2"));
        ids.put("oil", ingredient("做菜油" + n, "FAT", "900", "0", "0", "100"));
        ids.put("soy", ingredient("做菜酱油" + n, "OTHER", "60", "8", "5", "0"));
        ids.put("sugar", ingredient("做菜糖" + n, "OTHER", "400", "0", "100", "0"));
        ids.put("rice", ingredient("做菜大米" + n, "STAPLE", "346", "7.4", "77.9", "0.8"));
        ids.put("broccoli", ingredient("做菜西兰花" + n, "VEGETABLE", "34", "2.8", "6.6", "0.4"));
    }

    @Test
    @DisplayName("完整流程：记录一锅 → 出档位表 → 传照片 → 同步 → 配方带组件类型 → 配餐时汤汁跟肉走")
    void recordCalculateSync() throws Exception {
        long dishId = data(admin(post("/admin/meal/dishes"), Map.of(
                "name", "照烧鸡胸饭" + SEQ.get(), "category", "主食", "status", 0))).asLong();
        long recordId = data(admin(post("/admin/meal/cook-records"),
                Map.of("title", "照烧鸡胸饭", "dishId", dishId))).asLong();

        // 新建时带默认的四个组件
        JsonNode created = data(admin(get("/admin/meal/cook-records/" + recordId)));
        assertThat(created.path("content").path("components")).hasSize(4);
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");

        // 传一张步骤照片并读回
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        long photoId = data(admin(multipart("/admin/meal/cook-records/" + recordId + "/photos")
                .file(new MockMultipartFile("file", "step.jpg", "image/jpeg", jpeg)))).asLong();
        MvcResult photo = mockMvc.perform(admin(get("/admin/meal/cook-records/photos/" + photoId))).andReturn();
        assertThat(photo.getResponse().getStatus()).isEqualTo(200);
        assertThat(photo.getResponse().getContentType()).startsWith("image/jpeg");
        assertThat(photo.getResponse().getContentAsByteArray()).isEqualTo(jpeg);

        // 不支持的文件类型被拒绝
        JsonNode bad = call(admin(multipart("/admin/meal/cook-records/" + recordId + "/photos")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}))));
        assertThat(bad.path("code").asInt()).isNotZero();

        // 保存整锅内容，返回计算结果
        JsonNode detail = data(admin(put("/admin/meal/cook-records/" + recordId), update(dishId, photoId)));
        JsonNode calc = detail.path("calculation");
        assertThat(calc.path("blockers")).isEmpty();
        assertThat(calc.path("standard").path("weightG").asInt()).isEqualTo(446);
        assertThat(calc.path("standard").path("nutrients").path("kcal").asInt()).isEqualTo(603);
        JsonNode lunch160 = calc.path("tierTables").get(0).path("rows").get(3);
        assertThat(lunch160.path("parts").get(0).path("grams").asInt()).isEqualTo(185);
        assertThat(lunch160.path("parts").get(1).path("grams").asInt()).isEqualTo(49);

        // 步骤里引用别的记录的照片会被拒绝
        long otherRecord = data(admin(post("/admin/meal/cook-records"), Map.of("title", "另一锅"))).asLong();
        long otherPhoto = data(admin(multipart("/admin/meal/cook-records/" + otherRecord + "/photos")
                .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", jpeg)))).asLong();
        JsonNode stolen = call(admin(put("/admin/meal/cook-records/" + recordId), update(dishId, otherPhoto)));
        assertThat(stolen.path("code").asInt()).isNotZero();

        // 同步成菜品版本
        JsonNode sync = data(admin(post("/admin/meal/cook-records/" + recordId + "/sync"), Map.of()));
        long versionId = sync.path("versionId").asLong();
        assertThat(sync.path("dishId").asLong()).isEqualTo(dishId);

        DishVersionDO version = dishVersionMapper.selectById(versionId);
        assertThat(version.getNetWeightG()).isEqualTo(446);
        assertThat(version.getKcal()).isEqualTo(603);
        assertThat(version.getProteinG()).isEqualByComparingTo("53.4");
        assertThat(version.getRemark()).contains("做菜记录 #" + recordId);

        JsonNode dish = data(admin(get("/admin/meal/dishes/" + dishId)));
        JsonNode bom = dish.path("versions").get(0).path("bom");
        Map<Long, String> types = new LinkedHashMap<>();
        Map<Long, BigDecimal> grams = new LinkedHashMap<>();
        for (JsonNode line : bom) {
            types.put(line.path("ingredientId").asLong(), line.path("componentType").asText());
            grams.put(line.path("ingredientId").asLong(), new BigDecimal(line.path("netWeightG").asText()));
        }
        assertThat(types.get(ids.get("oil"))).isEqualTo("PROTEIN");
        assertThat(types.get(ids.get("soy"))).isEqualTo("SAUCE");
        assertThat(grams.get(ids.get("chicken"))).isEqualByComparingTo("200");
        assertThat(grams.get(ids.get("rice"))).isEqualByComparingTo("80");

        JsonNode afterSync = data(admin(get("/admin/meal/cook-records/" + recordId)));
        assertThat(afterSync.path("status").asText()).isEqualTo("SYNCED");
        assertThat(afterSync.path("syncedVersionNo").asInt()).isEqualTo(1);

        // 系统配餐：蛋白目标 91.6g 让主料组（肉+汤汁）放大 2 倍。
        // 汤汁跟着放大时碳水 = 6.5×2 + 62.32 + 3.96 = 79.28；若汤汁固定则只有 72.78。
        ComponentPortioner.Scaled scaled = componentPortioner.scale(version,
                new BigDecimal("91.6"), new BigDecimal("62.32"), new BigDecimal("8"), 0);
        assertThat(scaled).isNotNull();
        assertThat(scaled.carbG()).isEqualByComparingTo("79.28");
        assertThat(scaled.proteinG()).isEqualByComparingTo("99.20");
        assertThat(scaled.kcal()).isEqualTo(909);
        // 单餐重量按熟重：肉 150×2 + 汤汁 40×2 + 米饭 200 + 蔬菜 56 = 636g（按生重相加只有 452g）
        assertThat(scaled.weightG()).isEqualTo(636);

        // 同步后再改内容：回到未同步，但保留上次的版本
        Map<String, Object> changed = update(dishId, photoId);
        changed.put("remark", "盐少放一点");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) changed.get("content");
        content.put("portions", 4);
        JsonNode edited = data(admin(put("/admin/meal/cook-records/" + recordId), changed));
        assertThat(edited.path("status").asText()).isEqualTo("DRAFT");
        assertThat(edited.path("syncedVersionId").asLong()).isEqualTo(versionId);

        // 再做一锅：复制后熟重清空，需要重新称，因此不能直接同步
        long copyId = data(admin(post("/admin/meal/cook-records/" + recordId + "/copy"))).asLong();
        JsonNode copy = data(admin(get("/admin/meal/cook-records/" + copyId)));
        assertThat(copy.path("content").path("components").get(0).has("cookedG")).isFalse();
        assertThat(copy.path("content").path("ingredients")).hasSize(6);
        assertThat(copy.path("calculation").path("blockers").toString()).contains("熟重");
        JsonNode refused = call(admin(post("/admin/meal/cook-records/" + copyId + "/sync"), Map.of()));
        assertThat(refused.path("code").asInt()).isNotZero();

        // 列表
        JsonNode page = data(admin(get("/admin/meal/cook-records").param("keyword", "照烧")));
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(2);

        // 删除
        data(admin(delete("/admin/meal/cook-records/" + copyId)));
        assertThat(call(admin(get("/admin/meal/cook-records/" + copyId))).path("code").asInt()).isNotZero();
    }

    @Test
    @DisplayName("未关联菜品时不能同步")
    void syncNeedsDish() throws Exception {
        long recordId = data(admin(post("/admin/meal/cook-records"), Map.of("title", "无菜品"))).asLong();
        JsonNode r = call(admin(post("/admin/meal/cook-records/" + recordId + "/sync"), Map.of()));
        assertThat(r.path("code").asInt()).isNotZero();
        assertThat(r.path("msg").asText()).contains("菜品");
    }

    private Map<String, Object> update(long dishId, long photoId) {
        List<Map<String, Object>> ings = new ArrayList<>();
        ings.add(ing("a", "chicken", "1000", "meat"));
        ings.add(ing("b", "oil", "20", "meat"));
        ings.add(ing("c", "soy", "50", "sauce"));
        ings.add(ing("d", "sugar", "30", "sauce"));
        ings.add(ing("e", "rice", "400", "staple"));
        ings.add(ing("f", "broccoli", "300", "veg"));
        List<Map<String, Object>> parts = List.of(
                part("meat", "肉", "PROTEIN", "750"), part("sauce", "汤汁", "SAUCE", "200"),
                part("staple", "米饭", "STAPLE", "1000"), part("veg", "蔬菜", "VEGETABLE", "280"));
        List<Map<String, Object>> steps = List.of(new LinkedHashMap<>(Map.of(
                "key", "s1", "text", "鸡胸切块，酱油糖腌 20 分钟", "heat", "",
                "durationSec", 1200, "photoIds", List.of(photoId))));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("portions", 5);
        content.put("ingredients", ings);
        content.put("components", parts);
        content.put("steps", steps);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "照烧鸡胸饭");
        body.put("dishId", dishId);
        body.put("content", content);
        return body;
    }

    private Map<String, Object> ing(String key, String which, String grams, String part) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("ingredientId", ids.get(which));
        m.put("rawG", new BigDecimal(grams));
        m.put("componentKey", part);
        return m;
    }

    private Map<String, Object> part(String key, String name, String role, String cooked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("role", role);
        m.put("cookedG", new BigDecimal(cooked));
        return m;
    }

    private long ingredient(String name, String component, String kcal, String protein,
                            String carb, String fat) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("category", "测试");
        body.put("purchaseUnit", "kg");
        body.put("usageUnit", "g");
        body.put("conversionRate", 1000);
        body.put("edibleRate", 1);
        body.put("movingAvgPrice", new BigDecimal("20"));
        body.put("componentType", component);
        body.put("kcalPer100g", new BigDecimal(kcal));
        body.put("proteinPer100g", new BigDecimal(protein));
        body.put("carbPer100g", new BigDecimal(carb));
        body.put("fatPer100g", new BigDecimal(fat));
        body.put("status", 0);
        return data(admin(post("/admin/meal/ingredients"), body)).asLong();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return admin(builder).contentType(MediaType.APPLICATION_JSON).content(json(body));
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
