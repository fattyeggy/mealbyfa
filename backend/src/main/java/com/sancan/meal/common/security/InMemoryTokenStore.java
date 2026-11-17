package com.sancan.meal.common.security;

import com.sancan.meal.config.MealProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌存储，仅用于本机开发与测试。
 * 生产环境替换为 Redis 实现（保留相同接口），见交接手册 §2。
 */
@Component
public class InMemoryTokenStore implements TokenStore {

    private record Entry(LoginUser user, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryTokenStore(MealProperties properties) {
        this.ttl = Duration.ofMinutes(properties.getSecurity().getTokenTtlMinutes());
    }

    @Override
    public String issue(LoginUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Entry(user, Instant.now().plus(ttl)));
        return token;
    }

    @Override
    public Optional<LoginUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.user());
    }

    @Override
    public void invalidate(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** 测试与运维用：当前有效令牌数。 */
    public int size() {
        return tokens.size();
    }
}
