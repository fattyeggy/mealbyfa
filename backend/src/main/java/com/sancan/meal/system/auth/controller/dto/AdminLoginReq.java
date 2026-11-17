package com.sancan.meal.system.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginReq(
        @NotBlank(message = "账号不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}
