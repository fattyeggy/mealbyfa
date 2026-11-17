package com.sancan.meal.plan.enums;

/** 建档性别，仅用于基础代谢计算，不作为营养建议分类。 */
public enum Gender {
    MALE,
    FEMALE;

    public static Gender of(String value) {
        if (value == null) {
            return MALE;
        }
        try {
            return Gender.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MALE;
        }
    }
}
