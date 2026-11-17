package com.sancan.meal.menu.enums;

/** 餐次。小程序 Slide 内按此顺序纵向展示（手册 §3）。 */
public enum MealType {
    BREAKFAST(1, "早餐"),
    LUNCH(2, "午餐"),
    DINNER(3, "晚餐");

    private final int sort;
    private final String label;

    MealType(int sort, String label) {
        this.sort = sort;
        this.label = label;
    }

    public int getSort() {
        return sort;
    }

    public String getLabel() {
        return label;
    }
}
