package com.sancan.meal.plan.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.plan.controller.dto.PlanWeekResp;
import com.sancan.meal.plan.controller.dto.SwapPreviewResp;
import com.sancan.meal.plan.dal.dataobject.PlanItemDO;
import com.sancan.meal.plan.service.PlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 小程序个人计划与蛋白质替换（手册 §5）。 */
@RestController
@RequestMapping("/app/plan")
public class AppPlanController {

    private final PlanService planService;

    public AppPlanController(PlanService planService) {
        this.planService = planService;
    }

    /** 生成/重建该周个人计划。 */
    @PostMapping("/generate")
    public CommonResult<PlanWeekResp> generate(@RequestBody(required = false) GenerateReq req) {
        Long userId = UserContext.requireMemberId();
        LocalDate date = req == null || req.weekDate() == null ? LocalDate.now() : req.weekDate();
        return CommonResult.ok(planService.generate(userId, date));
    }

    @GetMapping("/week")
    public CommonResult<PlanWeekResp> week(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(planService.week(userId, date == null ? LocalDate.now() : date));
    }

    @GetMapping("/day")
    public CommonResult<PlanWeekResp.Day> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(planService.day(userId, date));
    }

    /** 已审核的可替换选项。 */
    @GetMapping("/swap/options")
    public CommonResult<java.util.List<java.util.Map<String, Object>>> swapOptions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam String mealType) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(planService.swapOptions(userId, serviceDate, mealType));
    }

    /** 试算替换后的克重、营养点与价格差，不落库。 */
    @PostMapping("/swap/preview")
    public CommonResult<SwapPreviewResp> previewSwap(@Valid @RequestBody SwapReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(planService.previewSwap(userId, req.serviceDate(), req.mealType(), req.dishVersionId()));
    }

    /** 提交替换。 */
    @PostMapping("/swap")
    public CommonResult<PlanItemDO> swap(@Valid @RequestBody SwapReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(planService.applySwap(userId, req.serviceDate(), req.mealType(), req.dishVersionId()));
    }

    public record GenerateReq(LocalDate weekDate) {
    }

    public record SwapReq(
            @NotNull(message = "服务日不能为空") LocalDate serviceDate,
            @NotNull(message = "餐次不能为空") String mealType,
            @NotNull(message = "目标菜品版本不能为空") Long dishVersionId) {
    }
}
