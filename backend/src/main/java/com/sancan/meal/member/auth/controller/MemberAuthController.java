package com.sancan.meal.member.auth.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.api.LoginResp;
import com.sancan.meal.member.auth.controller.dto.MemberLoginReq;
import com.sancan.meal.member.auth.service.MemberAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/auth")
public class MemberAuthController {

    private final MemberAuthService memberAuthService;

    public MemberAuthController(MemberAuthService memberAuthService) {
        this.memberAuthService = memberAuthService;
    }

    @PostMapping("/login")
    public CommonResult<LoginResp> login(@RequestBody MemberLoginReq req) {
        return CommonResult.ok(memberAuthService.login(req.code(), req.nickname(), req.avatar()));
    }
}
