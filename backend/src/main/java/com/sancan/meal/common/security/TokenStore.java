package com.sancan.meal.common.security;

import java.util.Optional;

/**
 * 令牌存储。本期内存实现；生产切 Redis 实现（对应手册 §2「缓存任务」层），
 * 业务代码只依赖本接口。
 */
public interface TokenStore {

    String issue(LoginUser user);

    Optional<LoginUser> resolve(String token);

    void invalidate(String token);
}
