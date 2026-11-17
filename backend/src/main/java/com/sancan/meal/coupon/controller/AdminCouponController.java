package com.sancan.meal.coupon.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import com.sancan.meal.coupon.dal.dataobject.CouponTemplateDO;
import com.sancan.meal.coupon.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 后台券管理与发放。 */
@RestController
@RequestMapping("/admin/meal")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @RequirePermission(Permission.COUPON_WRITE)
    @GetMapping("/coupon-templates")
    public CommonResult<List<CouponTemplateDO>> templates() {
        return CommonResult.ok(couponService.listTemplates());
    }

    @RequirePermission(Permission.COUPON_WRITE)
    @PostMapping("/coupon-templates")
    public CommonResult<Long> createTemplate(@Valid @RequestBody TemplateReq req) {
        return CommonResult.ok(couponService.createTemplate(new CouponService.TemplateReq(
                req.name(), req.type(), req.thresholdAmount(), req.discountAmount(),
                req.discountRate(), req.maxDiscountAmount(), req.validDays())));
    }

    @RequirePermission(Permission.COUPON_WRITE)
    @PostMapping("/coupons/grant")
    public CommonResult<CouponDO> grant(@Valid @RequestBody GrantReq req) {
        return CommonResult.ok(couponService.grant(req.userId(), req.templateId()));
    }

    @RequirePermission(Permission.COUPON_WRITE)
    @GetMapping("/coupons")
    public CommonResult<List<CouponDO>> coupons(@RequestParam Long userId,
                                                @RequestParam(required = false) String status) {
        return CommonResult.ok(couponService.listUserCoupons(userId, status));
    }

    public record TemplateReq(@NotBlank(message = "券名称不能为空") String name,
                              String type,
                              BigDecimal thresholdAmount,
                              BigDecimal discountAmount,
                              BigDecimal discountRate,
                              BigDecimal maxDiscountAmount,
                              Integer validDays) {
    }

    public record GrantReq(@NotNull(message = "用户不能为空") Long userId,
                           @NotNull(message = "券模板不能为空") Long templateId) {
    }
}
