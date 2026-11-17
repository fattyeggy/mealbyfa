package com.sancan.meal.plan.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 个人一周计划（小程序注册后首页与「计划」页）。
 *
 * <p>价格口径依据《计价规则 V1.0》：以「服务日」为单位定价，
 * 三餐展示价只是日价拆分（早 25%、午 40%、晚取余额），不构成三个独立报价。
 */
public record PlanWeekResp(
        Long planId,
        LocalDate weekStart,
        LocalDate weekEnd,
        BigDecimal dailyProteinG,
        /** 建档公式算出的原始目标，未夹取；与计划目标不同即说明触界。 */
        BigDecimal rawTargetProteinG,
        Integer dailyKcal,
        BigDecimal dailyPoints,
        /** 生成计划时绑定的定价版本。 */
        String pricingVersionNo,
        /** 是否存在待人工复核事项。 */
        boolean needsReview,
        List<String> reviewNotes,
        /** 未处理的复核事项明细。 */
        List<Review> reviews,
        /** 全周合计价格（各服务日日价之和）。 */
        BigDecimal weekPrice,
        List<Day> days,
        /** 过敏原冲突提示。 */
        List<String> warnings,
        /** 当周主题与地区，便于小程序复用首页 Hero。 */
        String themeName,
        String region) {

    public record Review(String reviewType, LocalDate serviceDate, String mealType,
                         String message, String status) {
    }

    public record Day(
            LocalDate serviceDate,
            int dayOfWeek,
            int totalWeightG,
            BigDecimal totalProteinG,
            int totalKcal,
            BigDecimal totalPoints,
            /** 到手日价，等于三餐展示价之和。 */
            BigDecimal totalPrice,
            BigDecimal dailyPrice,
            BigDecimal basePrice,
            BigDecimal extraFee,
            BigDecimal rawPrice,
            BigDecimal swapDifference,
            List<Meal> meals) {
    }

    public record Meal(
            String mealType,
            String mealLabel,
            String deliveryBatch,
            Long dishVersionId,
            Long dishId,
            String dishName,
            String dishCategory,
            String description,
            String imageUrl,
            Integer weightG,
            BigDecimal proteinG,
            Integer kcal,
            BigDecimal points,
            /** 三餐展示价，只用于展示与内部成本分析。 */
            BigDecimal price,
            List<String> allergens,
            boolean allergenConflict,
            List<String> conflictAllergens) {
    }
}
