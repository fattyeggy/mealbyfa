package com.sancan.meal.member.auth;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 后台预建档 → 用户手动输入手机号认领：必须有认领码；微信验证过的手机号不需要。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberClaimApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("手动输入手机号：没有认领码不能并入预建档，输错有次数限制，输对才合并")
    void manualPhoneNeedsClaimCode() throws Exception {
        String phone = "13955550001";
        JsonNode onboard = data(admin(post("/admin/meal/members/onboard"), onboardReq(phone)));
        String code = onboard.path("claimCode").asText();
        assertThat(code).matches("\\d{6}");

        String attacker = memberToken("claim-attacker");
        JsonNode noCode = call(member(post("/app/auth/bind-phone"), attacker, Map.of("phone", phone)));
        assertThat(noCode.path("code").asInt()).isEqualTo(1700);

        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            JsonNode r = call(member(post("/app/auth/bind-phone"), attacker, Map.of("phone", phone, "claimCode", wrong)));
            assertThat(r.path("code").asInt()).isNotZero();
        }
        // 第 6 次即使输对也被锁定
        JsonNode locked = call(member(post("/app/auth/bind-phone"), attacker, Map.of("phone", phone, "claimCode", code)));
        assertThat(locked.path("msg").asText()).contains("次数过多");

        String owner = memberToken("claim-owner");
        data(member(post("/app/auth/bind-phone"), owner, Map.of("phone", phone, "claimCode", code)));
        JsonNode status = data(member(get("/app/auth/status"), owner, null));
        assertThat(status.path("phoneBound").asBoolean()).isTrue();
        assertThat(status.path("onboardingCompleted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("微信验证过的手机号直接认领；没有预建档时手动绑定不需要认领码")
    void verifiedPhoneAndFreshPhone() throws Exception {
        String phone = "13955550002";
        data(admin(post("/admin/meal/members/onboard"), onboardReq(phone)));
        String user = memberToken("claim-wx");
        // mock 模式下微信手机号接口把 11 位数字当作已验证的手机号
        data(member(post("/app/auth/wechat-phone"), user, Map.of("code", phone)));
        assertThat(data(member(get("/app/auth/status"), user, null)).path("onboardingCompleted").asBoolean()).isTrue();

        String fresh = memberToken("claim-fresh");
        data(member(post("/app/auth/bind-phone"), fresh, Map.of("phone", "13955550003")));
        assertThat(data(member(get("/app/auth/status"), fresh, null)).path("phoneBound").asBoolean()).isTrue();
    }

    private Map<String, Object> onboardReq(String phone) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("goal", "cut");
        p.put("sex", "male");
        p.put("age", 30);
        p.put("heightCm", 175);
        p.put("weightKg", 75);
        p.put("dailyActivity", "sedentary");
        p.put("workoutsPerWeek", 4);
        p.put("workoutMinutes", 60);
        p.put("workoutType", "strength");
        p.put("workoutIntensity", "medium");
        p.put("healthFlags", List.of("none"));
        p.put("allergies", List.of());
        p.put("dietRestrictions", List.of());
        p.put("orderedMeals", List.of("lunch", "dinner"));
        p.put("privacyAccepted", true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("nickname", "预建档");
        body.put("profile", p);
        return body;
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
        return call(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "admin123"))))
                .path("data").path("token").asText();
    }

    private String memberToken(String code) throws Exception {
        return call(post("/app/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "nickname", "认领测试"))))
                .path("data").path("token").asText();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder b, Object body) throws Exception {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        b = b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private JsonNode call(MockHttpServletRequestBuilder b) throws Exception {
        return objectMapper.readTree(mockMvc.perform(b).andReturn().getResponse().getContentAsString());
    }

    private JsonNode data(MockHttpServletRequestBuilder b) throws Exception {
        JsonNode root = call(b);
        assertThat(root.path("code").asInt()).as("接口返回异常: " + root).isZero();
        return root.path("data");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
