package com.sancan.meal.wallet.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import com.sancan.meal.wallet.enums.FundSource;
import com.sancan.meal.wallet.enums.LedgerDirection;
import com.sancan.meal.wallet.service.WalletService;
import jakarta.validation.Valid;
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

/** 后台账户操作：看待账户、发 credit、人工调整、冲正（手册 §14 敏感操作）。 */
@RestController
@RequestMapping("/admin/meal")
public class AdminWalletController {

    private final WalletService walletService;

    public AdminWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @RequirePermission(Permission.WALLET_READ)
    @GetMapping("/wallet/{userId}")
    public CommonResult<WalletService.AccountView> account(@PathVariable Long userId) {
        return CommonResult.ok(walletService.view(userId));
    }

    @RequirePermission(Permission.WALLET_READ)
    @GetMapping("/wallet/{userId}/ledger")
    public CommonResult<PageResult<WalletLedgerDO>> ledger(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<WalletLedgerDO> list = walletService.ledger(userId, pageNo, pageSize);
        return CommonResult.ok(new PageResult<>(list, walletService.ledgerCount(userId)));
    }

    /** 发放赠送 credit：用户端只显示为「账户余额」，后台单独记录来源。 */
    @RequirePermission(Permission.WALLET_WRITE)
    @PostMapping("/wallet/{userId}/credit")
    public CommonResult<Boolean> grantCredit(@PathVariable Long userId,
                                             @Valid @RequestBody GrantReq req) {
        walletService.grantCredit(userId, req.amount(), req.reason());
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.WALLET_WRITE)
    @PostMapping("/wallet/{userId}/adjust")
    public CommonResult<Boolean> adjust(@PathVariable Long userId,
                                        @Valid @RequestBody AdjustReq req) {
        LedgerDirection direction = parse(LedgerDirection.class, req.direction());
        FundSource source = parse(FundSource.class, req.source());
        walletService.adjust(userId, direction, source, req.amount(), req.reason());
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.LEDGER_REVERSE)
    @PostMapping("/wallet/ledger/{ledgerId}/reverse")
    public CommonResult<Long> reverse(@PathVariable Long ledgerId,
                                      @Valid @RequestBody ReverseReq req) {
        return CommonResult.ok(walletService.reverse(ledgerId, req.reason()));
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "非法取值：" + value);
        }
    }

    public record GrantReq(@NotNull(message = "金额不能为空") BigDecimal amount,
                           String reason) {
    }

    public record AdjustReq(@NotNull(message = "方向不能为空") String direction,
                            @NotNull(message = "资金来源不能为空") String source,
                            @NotNull(message = "金额不能为空") BigDecimal amount,
                            String reason) {
    }

    public record ReverseReq(String reason) {
    }
}
