package com.sancan.meal.wallet.enums;

/** 账本业务类型。每一种都会在流水中留下不可删除的记录。 */
public enum LedgerBizType {
    /** 后台发放赠送 credit。 */
    CREDIT_GRANT,
    /** 真实充值（第二阶段）。 */
    TOPUP,
    /** 订单扣款，按资金来源拆成多条。 */
    ORDER_PAY,
    /** 订单退款，按原来源退回。 */
    REFUND,
    /** 人工调整（必须有原因与经办人）。 */
    ADJUST,
    /** 冲正：反向冲销某条历史流水。 */
    REVERSE
}
