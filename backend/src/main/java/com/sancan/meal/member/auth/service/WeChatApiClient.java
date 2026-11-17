package com.sancan.meal.member.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.config.MealProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信 code2Session：用 wx.login 的 code 换取 openid。
 * 失败信息按微信 errcode 原样返回，便于排查 AppID/域名/额度问题。
 */
@Component
public class WeChatApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatApiClient.class);
    private static final String ENDPOINT = "https://api.weixin.qq.com/sns/jscode2session";

    private final MealProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public WeChatApiClient(MealProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String exchangeOpenid(String code) {
        String appId = properties.getWx().getAppId();
        String secret = properties.getWx().getAppSecret();
        if (appId == null || appId.isBlank() || secret == null || secret.isBlank()) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR,
                    "未配置微信 AppID/AppSecret（环境变量 WX_APP_ID / WX_APP_SECRET）");
        }
        String url = ENDPOINT + "?appid=" + enc(appId) + "&secret=" + enc(secret)
                + "&js_code=" + enc(code) + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = objectMapper.readTree(response.body());
            int errcode = body.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("code2Session 失败 errcode={} errmsg={}", errcode, body.path("errmsg").asText());
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "微信登录失败（" + errcode + "）：" + body.path("errmsg").asText());
            }
            String openid = body.path("openid").asText("");
            if (openid.isBlank()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "微信登录失败：未返回 openid");
            }
            return openid;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("code2Session 请求异常", ex);
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "微信登录服务暂不可用，请稍后再试");
        }
    }

    /** 微信手机号快速验证：code 来自小程序 getPhoneNumber。 */
    public String getPhoneNumber(String code) {
        if (code != null && code.matches("1\\d{10}") && properties.getWx().isMockEnabled()) {
            return code;
        }
        String token = accessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + enc(token);
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of("code", code));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = objectMapper.readTree(response.body());
            int errcode = body.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "微信手机号获取失败（" + errcode + "）：" + body.path("errmsg").asText());
            }
            String phone = body.path("phone_info").path("purePhoneNumber").asText("");
            if (phone.isBlank()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "微信未返回手机号");
            }
            return phone;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "微信手机号服务暂不可用，请稍后再试");
        }
    }

    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    private synchronized String accessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }
        String appId = properties.getWx().getAppId();
        String secret = properties.getWx().getAppSecret();
        if (appId == null || appId.isBlank() || secret == null || secret.isBlank()) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR,
                    "未配置微信 AppID/AppSecret（环境变量 WX_APP_ID / WX_APP_SECRET）");
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + enc(appId) + "&secret=" + enc(secret);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = objectMapper.readTree(response.body());
            String token = body.path("access_token").asText("");
            if (token.isBlank()) {
                throw new ServiceException(ErrorCode.INTERNAL_ERROR,
                        "微信 access_token 获取失败：" + body.path("errmsg").asText());
            }
            cachedToken = token;
            tokenExpireAt = System.currentTimeMillis()
                    + Math.max(60, body.path("expires_in").asLong(7200) - 300) * 1000;
            return token;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "微信服务暂不可用，请稍后再试");
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
