package com.sancan.meal.nutrition.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.nutrition.service.GapService;
import com.sancan.meal.nutrition.service.NutritionEngine;
import com.sancan.meal.nutrition.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 小程序：六步建档 + 计算 + 每日缺口。 */
@RestController
@RequestMapping("/app/onboarding")
public class AppOnboardingController {

    private final OnboardingService onboardingService;
    private final GapService gapService;

    public AppOnboardingController(OnboardingService onboardingService, GapService gapService) {
        this.onboardingService = onboardingService;
        this.gapService = gapService;
    }

    @PostMapping
    public CommonResult<OnboardingResp> submit(@Valid @RequestBody OnboardingReqV2 req) {
        Long userId = UserContext.requireMemberId();
        OnboardingService.Outcome outcome = onboardingService.submit(userId, req);
        NutritionEngine.Result r = outcome.result();
        if (outcome.blocked()) {
            return CommonResult.ok(new OnboardingResp("blocked", outcome.reason(), outcome.action(), null));
        }
        return CommonResult.ok(new OnboardingResp("ok", null, null, new Plan(
                r.bmr(), r.activityFactor(), r.tdee(), r.kcal(),
                r.proteinRaw(), r.proteinTier(), r.fatG(), r.carbG(),
                r.flags(), r.expectedWeeklyChange(), "v1")));
    }

    @GetMapping("/gap")
    public CommonResult<GapService.Resp> gap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(gapService.compute(userId, date == null ? LocalDate.now() : date));
    }

    public record OnboardingResp(String status, String reason, String action, Plan plan) {
    }

    public record Plan(double bmr, double activityFactor, double tdee, int kcal,
                       int proteinRaw, int proteinTier, int fatG, int carbG,
                       java.util.List<String> flags, String expectedWeeklyChange, String configVersion) {
    }
}
