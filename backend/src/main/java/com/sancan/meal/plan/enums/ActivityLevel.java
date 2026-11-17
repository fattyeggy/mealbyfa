package com.sancan.meal.plan.enums;

/** 活动水平，用于 TDEE 估算。 */
public enum ActivityLevel {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    HIGH(1.725);

    private final double factor;

    ActivityLevel(double factor) {
        this.factor = factor;
    }

    public double getFactor() {
        return factor;
    }

    public static ActivityLevel of(String value) {
        if (value == null) {
            return SEDENTARY;
        }
        try {
            return ActivityLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SEDENTARY;
        }
    }
}
