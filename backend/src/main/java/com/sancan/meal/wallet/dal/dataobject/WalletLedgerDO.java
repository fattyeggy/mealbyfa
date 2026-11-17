package com.sancan.meal.wallet.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账本流水。刻意不继承 BaseDO：没有 updater、没有逻辑删除，
 * 对应表上方只有一个手写 Mapper（仅 insert / select），从代码层面保证不可改不可删。
 */
@Data
public class WalletLedgerDO {

    private Long id;
    private Long accountId;
    private Long userId;
    private String bizType;
    private String direction;
    private String source;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String refType;
    private Long refId;
    private Long reverseOfId;
    private String reason;
    private String operatorType;
    private Long operatorId;
    private LocalDateTime createTime;
}
