package com.sancan.meal.common.api;

import java.util.Set;

/** 登录响应。小程序与后台共用同一结构，通过 {@code userType} 区分。 */
public record LoginResp(String token, String userType, Long userId, String nickname, Set<String> roles) {
}
