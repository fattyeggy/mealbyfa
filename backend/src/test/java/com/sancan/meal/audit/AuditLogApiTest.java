package com.sancan.meal.audit;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 手册 §14：敏感操作必须留痕。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = adminToken();
    }

    @Test
    @DisplayName("发放 credit、人工调整、定价发布都会写入审计日志")
    void sensitiveOpsAreAudited() throws Exception {
        long userId = 880000 + SEQ.incrementAndGet();

        data(admin(post("/admin/meal/wallet/" + userId + "/credit"),
                Map.of("amount", 100, "reason", "审计用例")));
        data(admin(post("/admin/meal/wallet/" + userId + "/adjust"),
                Map.of("direction", "IN", "source", "CASH", "amount", 50, "reason", "审计用例调整")));

        long versionId = data(admin(post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "AUD-" + SEQ.incrementAndGet(),
                "dailyBasePrice", 66,
                "extraProteinPricePer10g", 1.9))).asLong();
        data(admin(post("/admin/meal/pricing/versions/" + versionId + "/review")));
        data(admin(post("/admin/meal/pricing/versions/" + versionId + "/publish")));

        JsonNode page = data(admin(get("/admin/meal/audit-logs?pageNo=1&pageSize=50")));
        List<String> actions = new ArrayList<>();
        for (JsonNode row : page.path("list")) {
            actions.add(row.path("action").asText());
        }
        assertThat(actions).contains("CREDIT_GRANT", "BALANCE_ADJUST", "PRICING_PUBLISH");

        JsonNode grant = findFirst(page, "CREDIT_GRANT", String.valueOf(userId));
        assertThat(grant.path("operatorType").asText()).isNotBlank();
        assertThat(grant.path("detail").asText()).contains("100");
    }

    private JsonNode findFirst(JsonNode page, String action, String targetId) {
        for (JsonNode row : page.path("list")) {
            if (action.equals(row.path("action").asText()) && targetId.equals(row.path("targetId").asText())) {
                return row;
            }
        }
        throw new AssertionError("未找到审计记录 " + action + " target=" + targetId);
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
