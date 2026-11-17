package com.sancan.meal.common.security;

/**
 * 接口权限点。与手册 §14「权限与后台角色」的可访问范围对应，
 * 粒度到「模块的读/写」，不做到按钮级。
 */
public enum Permission {
    MENU_READ,
    MENU_WRITE,
    DISH_WRITE,
    PRICING_READ,
    PRICING_WRITE,
    REVIEW_READ,
    REVIEW_WRITE,
    COUPON_WRITE,
    USER_READ,
    WALLET_READ,
    WALLET_WRITE,
    LEDGER_REVERSE,
    ORDER_READ,
    ORDER_LOCK,
    ORDER_REFUND,
    DELIVERY_WRITE,
    KITCHEN_READ,
    KITCHEN_WRITE,
    COST_READ,
    COST_WRITE,
    AUDIT_READ
}
