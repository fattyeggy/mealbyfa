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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
    private final HttpClient httpClient;

    public WeChatApiClient(MealProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = buildClient(properties.getWx());
    }

    /**
     * 默认正常校验证书。云托管开启「开放接口服务」时，平台代理会用自己的证书接管
     * api.weixin.qq.com 的 HTTPS，此时二选一：在云托管控制台关闭该服务；或把代理证书配置到
     * {@code meal.wx.ca-cert-path}（环境变量 WX_CA_CERT_PATH）。
     * {@code insecure-skip-tls-verify} 只作应急，开启时 AppSecret 与登录结果可被中间人截获或伪造。
     */
    static HttpClient buildClient(MealProperties.Wx wx) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8));
        try {
            if (wx.isInsecureSkipTlsVerify()) {
                log.error("微信 API 客户端已关闭证书校验（meal.wx.insecure-skip-tls-verify=true），仅限应急，请尽快改为配置 CA");
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
                return builder.sslContext(context).build();
            }
            if (wx.getCaCertPath() != null && !wx.getCaCertPath().isBlank()) {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{withExtraCa(Path.of(wx.getCaCertPath().trim()))},
                        new SecureRandom());
                log.info("微信 API 客户端额外信任 CA：{}", wx.getCaCertPath());
                return builder.sslContext(context).build();
            }
        } catch (Exception ex) {
            // 配错证书时宁可调用失败，也不退回不校验
            throw new IllegalStateException("微信 API 客户端证书配置无效：" + ex.getMessage(), ex);
        }
        return builder.build();
    }

    /** 系统默认 CA + 额外 CA：任一信任即通过。 */
    static X509TrustManager withExtraCa(Path pem) throws Exception {
        KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
        extra.load(null, null);
        int n = 0;
        try (InputStream in = Files.newInputStream(pem)) {
            for (Certificate cert : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                extra.setCertificateEntry("wx-extra-" + n++, cert);
            }
        }
        if (n == 0) {
            throw new IllegalStateException("文件里没有证书：" + pem);
        }
        X509TrustManager system = trustManager(null);
        X509TrustManager custom = trustManager(extra);
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                system.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    system.checkServerTrusted(chain, authType);
                } catch (CertificateException ex) {
                    custom.checkServerTrusted(chain, authType);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] a = system.getAcceptedIssuers();
                X509Certificate[] b = custom.getAcceptedIssuers();
                X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
                System.arraycopy(b, 0, all, a.length, b.length);
                return all;
            }
        };
    }

    private static X509TrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x) {
                return x;
            }
        }
        throw new IllegalStateException("没有可用的 X509TrustManager");
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }
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
