package com.sancan.meal.wallet;

import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import com.sancan.meal.wallet.enums.FundSource;
import com.sancan.meal.wallet.enums.LedgerBizType;
import com.sancan.meal.wallet.enums.LedgerDirection;
import com.sancan.meal.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 账本专项（手册 §16「支付与账本专项测试」）。
 * 重点：流水不可删、按来源退款、冲正反向关联、重复退款被唯一索引挡住。
 */
@SpringBootTest
@ActiveProfiles("test")
class WalletServiceTest {

    private static long seq = 5000;

    @Autowired
    private WalletService walletService;

    private long nextUser() {
        return ++seq;
    }

    @Test
    @DisplayName("发放 credit 必须填写原因")
    void grantRequiresReason() {
        long userId = nextUser();
        assertThatThrownBy(() -> walletService.grantCredit(userId, new BigDecimal("100"), "  "))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("原因");
    }

    @Test
    @DisplayName("每次余额变动都产生一条流水，且前后余额可对账")
    void everyChangeHasLedger() {
        long userId = nextUser();
        walletService.grantCredit(userId, new BigDecimal("100.00"), "阶段一赠送");
        walletService.adjust(userId, LedgerDirection.IN, FundSource.CASH, new BigDecimal("50.00"), "手工充值");

        List<WalletLedgerDO> ledgers = walletService.ledger(userId, 1, 10);
        assertThat(ledgers).hasSize(2);

        WalletLedgerDO cash = ledgers.get(0);
        assertThat(cash.getBizType()).isEqualTo(LedgerBizType.ADJUST.name());
        assertThat(cash.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(cash.getBalanceAfter()).isEqualByComparingTo("150.00");

        WalletLedgerDO credit = ledgers.get(1);
        assertThat(credit.getBizType()).isEqualTo(LedgerBizType.CREDIT_GRANT.name());
        assertThat(credit.getSource()).isEqualTo(FundSource.CREDIT.name());
        assertThat(credit.getBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("100.00");

        assertThat(walletService.view(userId).balance()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("退款按原来源退回：赠送 credit 不会变成现金")
    void refundKeepsSource() {
        long userId = nextUser();
        long orderId = 900001;
        walletService.grantCredit(userId, new BigDecimal("100.00"), "赠送");
        walletService.adjust(userId, LedgerDirection.IN, FundSource.CASH, new BigDecimal("50.00"), "充值");

        WalletService.Deduction deduction = walletService.deductForOrder(userId, orderId, new BigDecimal("120.00"));
        assertThat(deduction.creditAmount()).isEqualByComparingTo("100.00");
        assertThat(deduction.cashAmount()).isEqualByComparingTo("20.00");

        WalletService.AccountView afterPay = walletService.view(userId);
        assertThat(afterPay.balance()).isEqualByComparingTo("30.00");
        assertThat(afterPay.creditBalance()).isEqualByComparingTo("0.00");
        assertThat(afterPay.cashBalance()).isEqualByComparingTo("30.00");

        walletService.refundForOrder(userId, orderId, "服务取消");

        WalletService.AccountView afterRefund = walletService.view(userId);
        assertThat(afterRefund.balance()).isEqualByComparingTo("150.00");
        assertThat(afterRefund.creditBalance()).isEqualByComparingTo("100.00");
        assertThat(afterRefund.cashBalance()).isEqualByComparingTo("50.00");
        // 赠送部分回到 credit，没有变成可提现现金
        assertThat(afterRefund.cashBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("同一订单重复退款被账本唯一索引挡住，不会重复退钱")
    void doubleRefundRejected() {
        long userId = nextUser();
        long orderId = 900002;
        walletService.grantCredit(userId, new BigDecimal("100.00"), "赠送");
        walletService.deductForOrder(userId, orderId, new BigDecimal("60.00"));
        walletService.refundForOrder(userId, orderId, "第一次退款");

        assertThatThrownBy(() -> walletService.refundForOrder(userId, orderId, "第二次退款"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(walletService.view(userId).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("冲正生成反向流水并关联原流水，原流水保持不变")
    void reverseCreatesOppositeEntry() {
        long userId = nextUser();
        walletService.grantCredit(userId, new BigDecimal("80.00"), "误发");
        WalletLedgerDO original = walletService.ledger(userId, 1, 10).get(0);
        assertThat(original.getBalanceAfter()).isEqualByComparingTo("80.00");

        walletService.reverse(original.getId(), "金额重复发放");

        List<WalletLedgerDO> ledgers = walletService.ledger(userId, 1, 10);
        assertThat(ledgers).hasSize(2);
        WalletLedgerDO reverse = ledgers.get(0);
        assertThat(reverse.getBizType()).isEqualTo(LedgerBizType.REVERSE.name());
        assertThat(reverse.getDirection()).isEqualTo(LedgerDirection.OUT.name());
        assertThat(reverse.getReverseOfId()).isEqualTo(original.getId());
        assertThat(reverse.getAmount()).isEqualByComparingTo("80.00");
        assertThat(walletService.view(userId).balance()).isEqualByComparingTo("0.00");
        // 原流水仍在，未被改写
        assertThat(walletService.ledger(userId, 1, 10).get(1).getBalanceAfter()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("余额不足时扣款失败，且不留下任何流水")
    void insufficientBalanceLeavesNoLedger() {
        long userId = nextUser();
        walletService.grantCredit(userId, new BigDecimal("10.00"), "少量赠送");
        long before = walletService.ledgerCount(userId);

        assertThatThrownBy(() -> walletService.deductForOrder(userId, 900003L, new BigDecimal("99.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("余额");
        assertThat(walletService.ledgerCount(userId)).isEqualTo(before);
        assertThat(walletService.view(userId).balance()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("赠送 credit 优先于现金被扣除")
    void creditConsumedFirst() {
        long userId = nextUser();
        walletService.grantCredit(userId, new BigDecimal("30.00"), "赠送");
        walletService.adjust(userId, LedgerDirection.IN, FundSource.CASH, new BigDecimal("100.00"), "充值");
        WalletService.Deduction deduction = walletService.deductForOrder(userId, 900004L, new BigDecimal("50.00"));
        assertThat(deduction.creditAmount()).isEqualByComparingTo("30.00");
        assertThat(deduction.cashAmount()).isEqualByComparingTo("20.00");
        assertThat(walletService.view(userId).creditBalance()).isEqualByComparingTo("0.00");
    }
}
