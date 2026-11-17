package com.sancan.meal.system;

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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 手册 §14：不同角色只看/只做必要的事。
 * 账号由 AdminUserInitializer 初始化，测试里兜底补建。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPermissionTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedAccounts() {
        seed("admin", "admin123", "SUPER_ADMIN");
        seed("ops", "ops123", "OPS");
        seed("finance", "finance123", "FINANCE");
        seed("auditor", "auditor123", "AUDITOR");
    }

    private void seed(String username, String password, String role) {
        if (adminUserMapper.selectByUsername(username) != null) return;
        AdminUserDO user = new AdminUserDO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setRole(role);
        user.setStatus(0);
        adminUserMapper.insert(user);
    }

    @Test
    @DisplayName("运营可以发放 credit，财务与审计不能")
    void onlyOpsCanGrantCredit() throws Exception {
        long userId = 990000 + SEQ.incrementAndGet();
        String ops = token("ops", "ops123");
        mockMvc.perform(post("/admin/meal/wallet/" + userId + "/credit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ops)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 100, "reason", "运营发放"))))
                .andExpect(jsonPath("$.code").value(0));

        assertForbidden("finance", "finance123",
                post("/admin/meal/wallet/" + userId + "/credit"), Map.of("amount", 100, "reason", "财务越权"));
        assertForbidden("auditor", "auditor123",
                post("/admin/meal/wallet/" + userId + "/credit"), Map.of("amount", 100, "reason", "审计越权"));
    }

    @Test
    @DisplayName("财务不能改菜谱：菜单发布被拒绝")
    void financeCannotPublishMenu() throws Exception {
        assertForbidden("finance", "finance123",
                post("/admin/meal/menus/999999/publish"), null);
    }

    @Test
    @DisplayName("运营不能发布定价；财务可以")
    void pricingPublishRequiresFinance() throws Exception {
        assertForbidden("ops", "ops123", post("/admin/meal/pricing/versions"), Map.of(
                "versionNo", "OPS-" + SEQ.incrementAndGet(), "dailyBasePrice", 66));

        String finance = token("finance", "finance123");
        long id = data(post("/admin/meal/pricing/versions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + finance)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("versionNo", "FIN-" + SEQ.incrementAndGet(), "dailyBasePrice", 66)))).asLong();
        mockMvc.perform(post("/admin/meal/pricing/versions/" + id + "/review")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + finance))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/admin/meal/pricing/versions/" + id + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + finance))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("审计只能看：日志可读，写操作一律拒绝")
    void auditorIsReadOnly() throws Exception {
        String auditor = token("auditor", "auditor123");
        mockMvc.perform(get("/admin/meal/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditor))
                .andExpect(jsonPath("$.code").value(0));

        assertForbidden("auditor", "auditor123",
                post("/admin/meal/reviews/1/resolve"), Map.of("note", "x"));
        assertForbidden("auditor", "auditor123",
                post("/admin/meal/wallet/990999/credit"), Map.of("amount", 1, "reason", "x"));
    }

    @Test
    @DisplayName("运营没有审计权限")
    void opsCannotReadAudit() throws Exception {
        assertForbidden("ops", "ops123", get("/admin/meal/audit-logs"), null);
    }

    @Test
    @DisplayName("超级管理员拥有全部权限")
    void superAdminHasAll() throws Exception {
        String admin = token("admin", "admin123");
        mockMvc.perform(get("/admin/meal/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/admin/meal/wallet/991999/credit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 1, "reason", "管理员发放"))))
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== helpers ====================

    private void assertForbidden(String username, String password,
                                 MockHttpServletRequestBuilder builder, Object body) throws Exception {
        String token = token(username, password);
        builder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        mockMvc.perform(builder)
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("无权")));
    }

    private String token(String username, String password) throws Exception {
        JsonNode root = objectMapper.readTree(mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andReturn().getResponse().getContentAsString());
        assertThat(root.path("code").asInt()).as("登录失败: " + root).isZero();
        return root.path("data").path("token").asText();
    }

    private JsonNode data(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("code").asInt()).as("接口异常: " + root).isZero();
        return root.path("data");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
