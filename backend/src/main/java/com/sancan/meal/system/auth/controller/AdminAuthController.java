package com.sancan.meal.system.auth.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.api.LoginResp;
import com.sancan.meal.system.auth.controller.dto.AdminLoginReq;
import com.sancan.meal.system.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public CommonResult<LoginResp> login(@Valid @RequestBody AdminLoginReq req) {
        return CommonResult.ok(adminAuthService.login(req.username(), req.password()));
    }
}
