package com.sancan.meal.plan.review;

/** 复核事项类型（P4.6 §2.2）。 */
public enum ReviewType {
    /** 原始计划蛋白超出定价版本允许范围。 */
    PROTEIN_RANGE,
    /** 份量缩放触及 0.5–2.0 倍边界。 */
    SCALE_CLAMP,
    /** 日价触发最低价兜底。 */
    PRICE_FLOOR,
    /** 成本缺失或为 0，无法计价。 */
    COST_MISSING,
    /** 组件化配餐后全天热量/宏量超出允许偏差。 */
    MACRO_DEVIATION,
    /** 其他需人工确认事项。 */
    MANUAL
}
