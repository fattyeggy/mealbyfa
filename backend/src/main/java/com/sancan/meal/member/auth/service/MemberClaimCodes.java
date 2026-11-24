package com.sancan.meal.member.auth.service;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.config.MealProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认领码：后台按手机号建的占位档案，用户手动输入手机号认领时必须同时输入认领码。
 *
 * <p>手动输入的手机号没有经过验证，没有认领码的话，任何人输入别人的手机号就能把对方的档案、
 * 余额、券和订单并到自己名下。认领码由后台建档时显示给运营，再私下发给本人。
 * 码 = HMAC(密钥, 占位会员 ID + 手机号) 取 6 位数字，不落库；占位会员被认领删除后自然失效。
 * 微信手机号授权（已由微信验证）不需要认领码。
 */
@Component
public class MemberClaimCodes {

    private static final Logger log = LoggerFactory.getLogger(MemberClaimCodes.class);
    private static final String DEV_KEY = "sancan-dev-claim-key";
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 30L * 60 * 1000;

    private final MealProperties properties;
    private final Map<Long, Deque<Long>> failures = new ConcurrentHashMap<>();

    public MemberClaimCodes(MealProperties properties) {
        this.properties = properties;
    }

    public String codeFor(Long placeholderId, String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            byte[] h = mac.doFinal(("claim:" + placeholderId + ":" + phone).getBytes(StandardCharsets.UTF_8));
            long n = Integer.toUnsignedLong(ByteBuffer.wrap(h, 0, 4).getInt());
            return String.format("%06d", n % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("认领码计算失败", ex);
        }
    }

    /** 校验认领码；同一会员 30 分钟内最多错 5 次，防止穷举。 */
    public void verify(Long memberId, Long placeholderId, String phone, String input) {
        long now = System.currentTimeMillis();
        Deque<Long> recent = failures.computeIfAbsent(memberId, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW_MS) {
                recent.pollFirst();
            }
            if (recent.size() >= MAX_FAILURES) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "认领码错误次数过多，请 30 分钟后再试");
            }
            String expected = codeFor(placeholderId, phone);
            String given = input == null ? "" : input.replaceAll("\\s", "");
            if (given.isEmpty()) {
                throw new ServiceException(ErrorCode.CLAIM_CODE_REQUIRED);
            }
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    given.getBytes(StandardCharsets.UTF_8))) {
                recent.addLast(now);
                throw new ServiceException(ErrorCode.BAD_REQUEST, "认领码不正确，请向客服确认");
            }
            recent.clear();
        }
    }

    private byte[] key() {
        String secret = properties.getSecurity().getClaimSecret();
        if (secret == null || secret.isBlank()) {
            secret = properties.getWx().getAppSecret();
        }
        if (secret == null || secret.isBlank()) {
            if (!properties.getWx().isMockEnabled()) {
                log.warn("未配置 meal.security.claim-secret（CLAIM_SECRET）也没有微信 AppSecret，认领码使用开发密钥");
            }
            secret = DEV_KEY;
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
