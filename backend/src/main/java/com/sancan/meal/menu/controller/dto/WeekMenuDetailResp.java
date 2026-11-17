package com.sancan.meal.menu.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 周菜单详情（后台视角）：含成本，不含个人化信息。 */
public record WeekMenuDetailResp(
        Long id,
        String themeName,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        String version,
        String status,
        java.time.LocalDateTime publishTime,
        BigDecimal standardCost,
        BigDecimal expectedCostRate,
        String remark,
        int filledMeals,
        List<Day> days) {

    public record Day(
            LocalDate serviceDate,
            int dayOfWeek,
            int totalWeightG,
            BigDecimal totalProteinG,
            int totalKcal,
            BigDecimal totalPoints,
            List<Meal> meals) {
    }

    public record Meal(
            Long mealId,
            String mealType,
            String mealLabel,
            Integer sortNo,
            String deliveryBatch,
            Long dishVersionId,
            Long dishId,
            String dishName,
            String dishCategory,
            String description,
            String imageUrl,
            String storageMethod,
            String reheatMethod,
            Integer weightG,
            Integer kcal,
            BigDecimal proteinG,
            BigDecimal points,
            BigDecimal cost,
            List<String> allergens,
            Integer dishVersionStatus) {
    }
}
