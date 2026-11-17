package com.sancan.meal.plan.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 蛋白质替换试算。按《计价规则 V1.0》§6：
 * 差价 = (替换餐次变动成本 − 原餐次变动成本) ÷ 成本折算系数，可为负；
 * 整日只在最后取整一次。
 */
public record SwapPreviewResp(
        LocalDate serviceDate,
        String mealType,
        String mealLabel,
        Long fromDishVersionId,
        String fromDishName,
        Long toDishVersionId,
        String toDishName,
        BigDecimal fromVariableCost,
        BigDecimal toVariableCost,
        BigDecimal swapDifference,
        Snapshot before,
        Snapshot after,
        /** 到手日价变化，可为负。 */
        BigDecimal priceDelta,
        List<String> conflictAllergens,
        boolean needsReview,
        List<String> notes) {

    public record Snapshot(
            Integer weightG,
            BigDecimal proteinG,
            Integer kcal,
            BigDecimal points,
            BigDecimal price,
            BigDecimal dailyPrice) {
    }
}
