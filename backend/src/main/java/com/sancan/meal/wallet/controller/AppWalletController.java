package com.sancan.meal.wallet.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import com.sancan.meal.coupon.service.CouponService;
import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import com.sancan.meal.wallet.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 小程序「我的」：账户余额、流水与优惠券。 */
@RestController
@RequestMapping("/app")
public class AppWalletController {

    private final WalletService walletService;
    private final CouponService couponService;

    public AppWalletController(WalletService walletService, CouponService couponService) {
        this.walletService = walletService;
        this.couponService = couponService;
    }

    @GetMapping("/wallet")
    public CommonResult<WalletService.AccountView> wallet() {
        return CommonResult.ok(walletService.view(UserContext.requireMemberId()));
    }

    @GetMapping("/wallet/ledger")
    public CommonResult<PageResult<WalletLedgerDO>> ledger(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.requireMemberId();
        List<WalletLedgerDO> list = walletService.ledger(userId, pageNo, pageSize);
        return CommonResult.ok(new PageResult<>(list, walletService.ledgerCount(userId)));
    }

    /** 我的优惠券（含模板名称、门槛与面额，供下单页选券）。 */
    @GetMapping("/coupons")
    public CommonResult<List<CouponService.CouponView>> coupons(
            @RequestParam(required = false, defaultValue = "UNUSED") String status) {
        return CommonResult.ok(couponService.listViews(UserContext.requireMemberId(), status));
    }
}
