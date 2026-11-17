package com.sancan.meal.pricing.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.pricing.dal.dataobject.PricingVersionDO;
import com.sancan.meal.pricing.dal.dataobject.PricingVersionSwapDO;
import com.sancan.meal.pricing.service.DailyPricingService;
import com.sancan.meal.pricing.service.PricingVersionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 定价版本维护（计价规则 V1.0 §8）。改价必须新建版本再发布。 */
@RestController
@RequestMapping("/admin/meal/pricing")
public class PricingVersionController {

    private final PricingVersionService pricingVersionService;
    private final DailyPricingService dailyPricingService;

    public PricingVersionController(PricingVersionService pricingVersionService,
                                    DailyPricingService dailyPricingService) {
        this.pricingVersionService = pricingVersionService;
        this.dailyPricingService = dailyPricingService;
    }

    @RequirePermission(Permission.PRICING_READ)
    @GetMapping("/versions")
    public CommonResult<List<PricingVersionDO>> versions() {
        return CommonResult.ok(pricingVersionService.list());
    }

    @RequirePermission(Permission.PRICING_READ)
    @GetMapping("/active")
    public CommonResult<PricingVersionDO> active() {
        return CommonResult.ok(pricingVersionService.active());
    }

    /** 试算：给定计划蛋白与替换差价，返回完整价格明细。 */
    @RequirePermission(Permission.PRICING_READ)
    @GetMapping("/quote")
    public CommonResult<DailyPricingService.Quote> quote(
            @RequestParam int proteinG,
            @RequestParam(required = false) BigDecimal swapDifference) {
        return CommonResult.ok(dailyPricingService.quote(
                pricingVersionService.active(), proteinG, swapDifference));
    }

    @RequirePermission(Permission.PRICING_WRITE)
    @PostMapping("/versions")
    public CommonResult<Long> createDraft(@RequestBody PricingVersionService.DraftReq req) {
        return CommonResult.ok(pricingVersionService.createDraft(req));
    }

    @RequirePermission(Permission.PRICING_WRITE)
    @PostMapping("/versions/{id}/review")
    public CommonResult<Boolean> review(@PathVariable Long id) {
        pricingVersionService.review(id);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.PRICING_WRITE)
    @PostMapping("/versions/{id}/publish")
    public CommonResult<Boolean> publish(@PathVariable Long id) {
        pricingVersionService.publish(id);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.PRICING_READ)
    @GetMapping("/versions/{id}/swaps")
    public CommonResult<List<PricingVersionSwapDO>> swaps(@PathVariable Long id) {
        return CommonResult.ok(pricingVersionService.swaps(id));
    }

    @RequirePermission(Permission.PRICING_WRITE)
    @PostMapping("/versions/{id}/swaps")
    public CommonResult<Long> addSwap(@PathVariable Long id, @Valid @RequestBody SwapReq req) {
        return CommonResult.ok(pricingVersionService.addSwap(
                id, req.mealType(), req.fromDishVersionId(), req.toDishVersionId(),
                req.costDelta(), req.remark()));
    }

    public record SwapReq(@NotBlank(message = "餐次不能为空") String mealType,
                          @NotNull(message = "原菜品版本不能为空") Long fromDishVersionId,
                          @NotNull(message = "替换菜品版本不能为空") Long toDishVersionId,
                          BigDecimal costDelta,
                          String remark) {
    }
}
