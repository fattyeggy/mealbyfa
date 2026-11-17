package com.sancan.meal.wallet.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.audit.service.AuditLogService;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.wallet.dal.dataobject.WalletAccountDO;
import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import com.sancan.meal.wallet.dal.mapper.WalletAccountMapper;
import com.sancan.meal.wallet.dal.mapper.WalletLedgerMapper;
import com.sancan.meal.wallet.enums.FundSource;
import com.sancan.meal.wallet.enums.LedgerBizType;
import com.sancan.meal.wallet.enums.LedgerDirection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 账户与账本（手册 §7）。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li>禁止直接覆盖余额字段，每次变动必须写一条不可删除的流水；</li>
 *   <li>赠送 credit 与真实现金分账，退款按原来源退回，不得把赠送退成现金；</li>
 *   <li>错误记录只能冲正，不能修改历史。</li>
 * </ol>
 */
@Service
public class WalletService {

    private static final int MONEY_SCALE = 2;

    private final WalletAccountMapper accountMapper;
    private final WalletLedgerMapper ledgerMapper;
    private final AuditLogService auditLogService;

    public WalletService(WalletAccountMapper accountMapper, WalletLedgerMapper ledgerMapper,
                         AuditLogService auditLogService) {
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.auditLogService = auditLogService;
    }

    /** 账户视图。 */
    public record AccountView(Long userId, BigDecimal balance, BigDecimal creditBalance,
                              BigDecimal cashBalance, int version) {
    }

    /** 扣款结果：按来源拆分的两笔流水金额。 */
    public record Deduction(BigDecimal creditAmount, BigDecimal cashAmount) {
        public BigDecimal total() {
            return creditAmount.add(cashAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }

    public WalletAccountDO ensureAccount(Long userId) {
        WalletAccountDO account = accountMapper.selectByUserId(userId);
        if (account != null) {
            return account;
        }
        account = new WalletAccountDO();
        account.setUserId(userId);
        account.setBalance(BigDecimal.ZERO.setScale(MONEY_SCALE));
        account.setCreditBalance(BigDecimal.ZERO.setScale(MONEY_SCALE));
        account.setCashBalance(BigDecimal.ZERO.setScale(MONEY_SCALE));
        account.setVersion(0);
        try {
            accountMapper.insert(account);
            return account;
        } catch (DuplicateKeyException ex) {
            // 并发首单：另一个请求已建号，直接读回
            return accountMapper.selectByUserId(userId);
        }
    }

    public AccountView view(Long userId) {
        WalletAccountDO account = ensureAccount(userId);
        return new AccountView(account.getUserId(), account.getBalance(),
                account.getCreditBalance(), account.getCashBalance(),
                account.getVersion() == null ? 0 : account.getVersion());
    }

    /**
     * 余额是否足够。锁单流程先用它做预检：余额不足时只写「失败原因」，
     * 不进入扣款逻辑，避免业务异常把整个事务标记为回滚导致失败原因丢失。
     */
    public boolean canPay(Long userId, BigDecimal amount) {
        BigDecimal payable = amount == null ? BigDecimal.ZERO : amount;
        return ensureAccount(userId).getBalance().compareTo(payable) >= 0;
    }

    public List<WalletLedgerDO> ledger(Long userId, int pageNo, int pageSize) {
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(200, Math.max(1, pageSize));
        return ledgerMapper.selectPage(userId, (safePage - 1) * safeSize, safeSize);
    }

    public long ledgerCount(Long userId) {
        return ledgerMapper.countByUserId(userId);
    }

    /**
     * 发放赠送 credit。必须填写原因，后台另记来源（手册 §15 阶段一特别规则）。
     */
    @Transactional
    public void grantCredit(Long userId, BigDecimal amount, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(ErrorCode.CREDIT_GRANT_REASON_REQUIRED);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "发放金额必须大于 0");
        }
        WalletAccountDO account = ensureAccount(userId);
        applyDelta(account, FundSource.CREDIT, amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                LedgerBizType.CREDIT_GRANT, LedgerDirection.IN, null, null, null, reason);
        auditLogService.record("CREDIT_GRANT", "USER", userId, "发放 " + amount + " 元：" + reason);
    }

    /** 人工调增/调减余额，必须填写原因（手册 §14 敏感操作）。 */
    @Transactional
    public void adjust(Long userId, LedgerDirection direction, FundSource source,
                       BigDecimal amount, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(ErrorCode.CREDIT_GRANT_REASON_REQUIRED, "人工调整余额必须填写原因");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "调整金额必须大于 0");
        }
        WalletAccountDO account = ensureAccount(userId);
        applyDelta(account, source, amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                LedgerBizType.ADJUST, direction, null, null, null, reason);
        auditLogService.record("BALANCE_ADJUST", "USER", userId,
                direction + " " + source + " " + amount + " 元：" + reason);
    }

    /**
     * 订单扣款。赠送 credit 优先使用，不足部分走现金余额。
     * 余额不足直接抛异常，调用方据此保持订单未锁定状态。
     */
    @Transactional
    public Deduction deductForOrder(Long userId, Long orderId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return new Deduction(BigDecimal.ZERO.setScale(MONEY_SCALE),
                    BigDecimal.ZERO.setScale(MONEY_SCALE));
        }
        WalletAccountDO account = ensureAccount(userId);
        BigDecimal payable = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (account.getBalance().compareTo(payable) < 0) {
            throw new ServiceException(ErrorCode.WALLET_BALANCE_INSUFFICIENT,
                    "账户余额 " + account.getBalance() + " 元，不足以支付 " + payable + " 元");
        }
        BigDecimal creditUsed = account.getCreditBalance().min(payable);
        BigDecimal cashUsed = payable.subtract(creditUsed).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (creditUsed.signum() > 0) {
            account = reload(account);
            applyDelta(account, FundSource.CREDIT, creditUsed, LedgerBizType.ORDER_PAY,
                    LedgerDirection.OUT, "ORDER", orderId, null, "订单支付");
        }
        if (cashUsed.signum() > 0) {
            account = reload(account);
            applyDelta(account, FundSource.CASH, cashUsed, LedgerBizType.ORDER_PAY,
                    LedgerDirection.OUT, "ORDER", orderId, null, "订单支付");
        }
        return new Deduction(creditUsed, cashUsed);
    }

    /**
     * 订单退款：按原支付流水的来源逐笔退回。
     * 唯一索引 (biz_type, ref_type, ref_id, source) 保证同一订单同来源只能退一次。
     */
    @Transactional
    public void refundForOrder(Long userId, Long orderId, String reason) {
        List<WalletLedgerDO> paid = ledgerMapper.selectByRef(
                LedgerBizType.ORDER_PAY.name(), "ORDER", orderId);
        if (paid.isEmpty()) {
            return;
        }
        auditLogService.record("ORDER_REFUND", "ORDER", orderId,
                "退款 " + paid.stream().map(WalletLedgerDO::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                        + " 元：" + (reason == null ? "" : reason));
        for (WalletLedgerDO entry : paid) {
            WalletAccountDO account = ensureAccount(userId);
            applyDelta(account, FundSource.valueOf(entry.getSource()), entry.getAmount(),
                    LedgerBizType.REFUND, LedgerDirection.IN, "ORDER", orderId, null,
                    reason == null || reason.isBlank() ? "订单退款" : reason);
        }
    }

    /** 冲正：生成反向流水并关联原流水，原流水保持不动。 */
    @Transactional
    public Long reverse(Long ledgerId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "冲正必须填写原因");
        }
        WalletLedgerDO original = ledgerMapper.selectById(ledgerId);
        if (original == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "流水不存在：" + ledgerId);
        }
        if (LedgerBizType.REVERSE.name().equals(original.getBizType())) {
            throw new ServiceException(ErrorCode.LEDGER_RECORD_IMMUTABLE, "冲正流水本身不能再被冲正");
        }
        LedgerDirection reverseDirection = LedgerDirection.IN.name().equals(original.getDirection())
                ? LedgerDirection.OUT : LedgerDirection.IN;
        WalletAccountDO account = ensureAccount(original.getUserId());
        applyDelta(account, FundSource.valueOf(original.getSource()), original.getAmount(),
                LedgerBizType.REVERSE, reverseDirection, "LEDGER", original.getId(),
                original.getId(), reason);
        auditLogService.record("LEDGER_REVERSE", "LEDGER", original.getId(), "冲正：" + reason);
        return original.getId();
    }

    // ==================== 内部 ====================

    private WalletAccountDO reload(WalletAccountDO account) {
        return accountMapper.selectById(account.getId());
    }

    /**
     * 统一记账入口：更新余额（乐观锁）+ 写入一条流水。
     * 余额不足或版本冲突都会让整个事务回滚，不会出现「余额变了但没流水」。
     */
    private void applyDelta(WalletAccountDO account, FundSource source, BigDecimal amount,
                            LedgerBizType bizType, LedgerDirection direction,
                            String refType, Long refId, Long reverseOfId, String reason) {
        BigDecimal signed = LedgerDirection.IN == direction ? amount : amount.negate();
        BigDecimal before = account.getBalance().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal after = before.add(signed).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (after.signum() < 0) {
            throw new ServiceException(ErrorCode.WALLET_BALANCE_INSUFFICIENT);
        }
        BigDecimal creditBefore = account.getCreditBalance();
        BigDecimal cashBefore = account.getCashBalance();
        BigDecimal creditAfter = source == FundSource.CREDIT ? creditBefore.add(signed) : creditBefore;
        BigDecimal cashAfter = source == FundSource.CASH ? cashBefore.add(signed) : cashBefore;
        if (creditAfter.signum() < 0 || cashAfter.signum() < 0) {
            throw new ServiceException(ErrorCode.WALLET_BALANCE_INSUFFICIENT);
        }

        int version = account.getVersion() == null ? 0 : account.getVersion();
        int rows = accountMapper.update(null, new LambdaUpdateWrapper<WalletAccountDO>()
                .eq(WalletAccountDO::getId, account.getId())
                .eq(WalletAccountDO::getVersion, version)
                .set(WalletAccountDO::getBalance, after)
                .set(WalletAccountDO::getCreditBalance, creditAfter)
                .set(WalletAccountDO::getCashBalance, cashAfter)
                .set(WalletAccountDO::getVersion, version + 1)
                .set(WalletAccountDO::getUpdater, UserContext.currentActor())
                .set(WalletAccountDO::getUpdateTime, java.time.LocalDateTime.now()));
        if (rows == 0) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "账户余额更新冲突，请重试");
        }
        account.setBalance(after);
        account.setCreditBalance(creditAfter);
        account.setCashBalance(cashAfter);
        account.setVersion(version + 1);

        String actor = UserContext.currentActor();
        WalletLedgerDO ledger = new WalletLedgerDO();
        ledger.setAccountId(account.getId());
        ledger.setUserId(account.getUserId());
        ledger.setBizType(bizType.name());
        ledger.setDirection(direction.name());
        ledger.setSource(source.name());
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setRefType(refType);
        ledger.setRefId(refId);
        ledger.setReverseOfId(reverseOfId);
        ledger.setReason(reason);
        String[] parts = actor.split(":", 2);
        ledger.setOperatorType(parts[0]);
        ledger.setOperatorId(parts.length > 1 ? parseLong(parts[1]) : null);
        ledgerMapper.insert(ledger);
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
