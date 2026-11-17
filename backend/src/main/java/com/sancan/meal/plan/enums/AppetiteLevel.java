package com.sancan.meal.plan.enums;

/** 食量，用于微调目标蛋白质。手册 §5 将「食量」列为建档必填项。 */
public enum AppetiteLevel {
    SMALL(0.90),
    NORMAL(1.00),
    LARGE(1.10);

    private final double factor;

    AppetiteLevel(double factor) {
        this.factor = factor;
    }

    public double getFactor() {
        return factor;
    }

    public static AppetiteLevel of(String value) {
        if (value == null) {
            return NORMAL;
        }
        try {
            return AppetiteLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NORMAL;
        }
    }
}
