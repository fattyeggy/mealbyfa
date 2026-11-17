package com.sancan.meal.system.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 后台登录：正确凭据签发令牌，错误凭据统一拒绝。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareAdmin() {
        if (adminUserMapper.selectByUsername("admin") == null) {
            AdminUserDO user = new AdminUserDO();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setNickname("超级管理员");
            user.setRole("SUPER_ADMIN");
            user.setStatus(0);
            adminUserMapper.insert(user);
        }
    }

    @Test
    @DisplayName("正确账号密码可换取令牌，且令牌可用于受保护接口")
    void loginSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userType").value("ADMIN"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("data").path("token").asText();
        assertThat(token).hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("密码错误返回 401，且不泄露账号是否存在")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }

    @Test
    @DisplayName("账号不存在与密码错误返回相同提示")
    void loginWithUnknownUser() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "nobody", "password", "any"))))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }

    @Test
    @DisplayName("缺少参数时返回 400 并指出字段")
    void loginWithBlankParams() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "", "password", ""))))
                .andExpect(jsonPath("$.code").value(400));
    }
}
