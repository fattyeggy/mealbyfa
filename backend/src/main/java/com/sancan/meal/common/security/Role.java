package com.sancan.meal.common.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * 后台角色，严格对应手册 §14 的权限表。
 *
 * <p>命名取中文角色的英文缩写，后台展示时再翻译。
 */
public enum Role {

    /** 超级管理员：全部模块。 */
    SUPER_ADMIN(EnumSet.allOf(Permission.class), "超级管理员"),

    /** 运营：菜单、用户、订单、优惠券、credit；不能改支付密钥（支付本就未接入）。 */
    OPS(EnumSet.of(Permission.MENU_READ, Permission.MENU_WRITE, Permission.DISH_WRITE,
            Permission.USER_READ, Permission.ORDER_READ, Permission.ORDER_LOCK, Permission.COUPON_WRITE,
            Permission.WALLET_READ, Permission.WALLET_WRITE, Permission.REVIEW_READ,
            Permission.REVIEW_WRITE, Permission.KITCHEN_READ), "运营"),

    /** 营养计划人员：档案、计划、菜谱营养；不能直接改余额。 */
    NUTRITIONIST(EnumSet.of(Permission.MENU_READ, Permission.MENU_WRITE, Permission.DISH_WRITE,
            Permission.USER_READ, Permission.REVIEW_READ, Permission.REVIEW_WRITE,
            Permission.PRICING_READ), "营养计划"),

    /** 厨房负责人：生产、领料、分装、损耗；不看非必要身体数据。 */
    KITCHEN(EnumSet.of(Permission.KITCHEN_READ, Permission.KITCHEN_WRITE,
            Permission.ORDER_READ, Permission.MENU_READ), "厨房负责人"),

    /** 采购：食材与价格；不能改订单和余额。 */
    PURCHASER(EnumSet.of(Permission.MENU_READ, Permission.DISH_WRITE,
            Permission.COST_READ, Permission.KITCHEN_READ), "采购"),

    /** 财务：支付、退款、账本、成本与报表；不能改菜谱。 */
    FINANCE(EnumSet.of(Permission.WALLET_READ, Permission.LEDGER_REVERSE,
            Permission.ORDER_READ, Permission.ORDER_LOCK, Permission.ORDER_REFUND, Permission.DELIVERY_WRITE,
            Permission.COST_READ, Permission.COST_WRITE, Permission.PRICING_READ,
            Permission.PRICING_WRITE, Permission.AUDIT_READ), "财务"),

    /** 配送：配送清单与状态；不看营养与财务。 */
    COURIER(EnumSet.of(Permission.ORDER_READ, Permission.DELIVERY_WRITE), "配送"),

    /** 只读审计：日志、报表、历史版本；不能新增或修改。 */
    AUDITOR(EnumSet.of(Permission.AUDIT_READ, Permission.COST_READ, Permission.ORDER_READ,
            Permission.MENU_READ, Permission.PRICING_READ, Permission.WALLET_READ), "只读审计");

    private final Set<Permission> permissions;
    private final String label;

    Role(Set<Permission> permissions, String label) {
        this.permissions = permissions;
        this.label = label;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public String getLabel() {
        return label;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public static Role of(String value) {
        if (value == null) {
            return OPS;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return OPS;
        }
    }
}
