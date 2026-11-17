package com.sancan.meal.member.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 小程序登录与鉴权：mock openid 稳定、无令牌被拒绝、令牌可访问「我的」。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberAuthApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("同一 code 多次登录得到同一用户")
    void mockLoginIsStable() throws Exception {
        long first = loginAndGetUserId("code-stable-001");
        long second = loginAndGetUserId("code-stable-001");
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("令牌可访问 /app/me 并返回本人信息")
    void meWithToken() throws Exception {
        String token = login("code-me-001", "测试用户");
        mockMvc.perform(get("/app/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("测试用户"));
    }

    @Test
    @DisplayName("无令牌访问受保护接口返回 401")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/app/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("小程序令牌不能访问后台接口（端点域隔离）")
    void memberTokenCannotAccessAdmin() throws Exception {
        String token = login("code-iso-001", "越权测试");
        mockMvc.perform(get("/admin/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("令牌有效但访问不存在接口返回 404，而非 500")
    void unknownEndpointReturns404() throws Exception {
        String token = login("code-iso-002", "404测试");
        mockMvc.perform(get("/app/not-exists").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("缺少 code 返回 400")
    void loginWithoutCode() throws Exception {
        mockMvc.perform(post("/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(jsonPath("$.code").value(400));
    }

    private long loginAndGetUserId(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("userId").asLong();
    }

    private String login(String code, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "nickname", nickname))))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
