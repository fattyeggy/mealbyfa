package com.sancan.meal.plan.enums;

/** 目标。产品不承诺固定减重结果（手册 §1），此处只用于估算目标蛋白质与热量。 */
public enum DietGoal {
    LOSE,
    MAINTAIN,
    GAIN;

    public static DietGoal of(String value) {
        if (value == null) {
            return MAINTAIN;
        }
        try {
            return DietGoal.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MAINTAIN;
        }
    }
}
