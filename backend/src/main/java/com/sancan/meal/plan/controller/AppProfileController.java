package com.sancan.meal.plan.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.plan.controller.dto.OnboardingReq;
import com.sancan.meal.plan.controller.dto.ProfileResp;
import com.sancan.meal.plan.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序 Onboarding 与档案（手册 §5）。 */
@RestController
@RequestMapping("/app/profile")
public class AppProfileController {

    private final ProfileService profileService;

    public AppProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public CommonResult<ProfileResp> get() {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(profileService.toResp(profileService.find(userId)));
    }

    @PutMapping
    public CommonResult<ProfileResp> save(@Valid @RequestBody OnboardingReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(profileService.save(userId, req));
    }
}
