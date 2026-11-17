package com.sancan.meal.admin.onboarding;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台直接建档（按手机号匹配）。 */
@RestController
@RequestMapping("/admin/meal/members")
public class AdminMemberOnboardingController {

    private final AdminMemberOnboardingService service;

    public AdminMemberOnboardingController(AdminMemberOnboardingService service) {
        this.service = service;
    }

    @RequirePermission({Permission.USER_READ, Permission.WALLET_WRITE})
    @PostMapping("/onboard")
    public CommonResult<AdminMemberOnboardingService.Resp> onboard(
            @Valid @RequestBody AdminMemberOnboardingService.Req req) {
        return CommonResult.ok(service.onboard(req));
    }
}
