package com.sancan.meal.feedback.enums;

/** 剩余比例，对应手册 §5「吃完、剩四分之一、剩一半或未吃」。 */
public enum LeftoverRatio {
    EATEN("吃完"),
    QUARTER("剩四分之一"),
    HALF("剩一半"),
    NONE("未吃");

    private final String label;

    LeftoverRatio(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 折算的进食比例，用于周复盘统计。 */
    public double eatenRatio() {
        return switch (this) {
            case EATEN -> 1.0;
            case QUARTER -> 0.75;
            case HALF -> 0.5;
            case NONE -> 0.0;
        };
    }
}
