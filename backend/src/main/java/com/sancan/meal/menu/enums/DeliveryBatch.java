package com.sancan.meal.menu.enums;

/**
 * 配送批次。手册 §14 规定每日两次配送：
 * 午餐一批；晚餐与次日早餐一批（早餐以冷食或短时复热交付）。
 */
public enum DeliveryBatch {
    BATCH_1("午餐批次"),
    BATCH_2("晚餐及次日早餐批次");

    private final String label;

    DeliveryBatch(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 餐次默认归属批次：早餐随前一晚的晚餐批次送达。 */
    public static DeliveryBatch forMealType(MealType mealType) {
        return mealType == MealType.LUNCH ? BATCH_1 : BATCH_2;
    }
}
