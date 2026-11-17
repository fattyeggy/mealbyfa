package com.sancan.meal.order.enums;

/** 服务日订单状态，与手册 §8 的状态链一致。 */
public enum OrderStatus {
    PENDING_CONFIRM,
    PENDING_LOCK,
    LOCKED,
    PURCHASE_AGG,
    PRODUCING,
    LUNCH_DELIVERING,
    LUNCH_DONE,
    DINNER_DELIVERING,
    DINNER_DONE,
    COMPLETED,
    PAUSED,
    CANCELLED,
    REFUNDED;

    /** 面向用户与后台的中文状态名。 */
    public String label() {
        return switch (this) {
            case PENDING_CONFIRM -> "待确认";
            case PENDING_LOCK -> "待锁定";
            case LOCKED -> "已锁定";
            case PURCHASE_AGG -> "采购汇总";
            case PRODUCING -> "生产中";
            case LUNCH_DELIVERING -> "午餐配送中";
            case LUNCH_DONE -> "午餐已送达";
            case DINNER_DELIVERING -> "晚餐配送中";
            case DINNER_DONE -> "晚餐已送达";
            case COMPLETED -> "已完成";
            case PAUSED -> "已暂停";
            case CANCELLED -> "已取消";
            case REFUNDED -> "已退款";
        };
    }

    public boolean isLocked() {
        return this == LOCKED || this == PURCHASE_AGG || this == PRODUCING
                || this == LUNCH_DELIVERING || this == LUNCH_DONE
                || this == DINNER_DELIVERING || this == DINNER_DONE || this == COMPLETED;
    }
}
