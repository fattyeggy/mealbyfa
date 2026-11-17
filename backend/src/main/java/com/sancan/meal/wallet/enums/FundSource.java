package com.sancan.meal.wallet.enums;

/**
 * 资金来源。赠送 credit 与真实余额分账，退款必须按原来源退回，
 * 不得把赠送 credit 退成现金（手册 §7）。
 */
public enum FundSource {
    CREDIT,
    CASH
}
