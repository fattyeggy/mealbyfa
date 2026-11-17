package com.sancan.meal.common.security;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;

/**
 * 请求级登录态。由 {@link AuthInterceptor} 写入与清理，
 * 业务层与 MyBatis 审计字段统一从这里取操作人。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    public static LoginUser get() {
        return CURRENT.get();
    }

    public static LoginUser require() {
        LoginUser user = CURRENT.get();
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireAdminId() {
        LoginUser user = require();
        if (user.getUserType() != UserType.ADMIN) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }
        return user.getId();
    }

    public static Long requireMemberId() {
        LoginUser user = require();
        if (user.getUserType() != UserType.MEMBER) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }
        return user.getId();
    }

    /** 审计字段用：无登录态时记为 system（定时任务等场景）。 */
    public static String currentActor() {
        LoginUser user = CURRENT.get();
        if (user == null) {
            return "system";
        }
        return user.getUserType() + ":" + user.getId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
