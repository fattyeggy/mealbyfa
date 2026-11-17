package com.sancan.meal.menu.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公共周菜单（小程序未注册视角，手册 §3）。
 * 不返回价格与克重个人化结果，只给标准菜品信息。
 */
public record PublicWeekResp(
        Long menuId,
        String themeName,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        boolean published,
        /** 未发布时提示预计发布时间。 */
        LocalDateTime expectedPublishTime,
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
            String mealType,
            String mealLabel,
            String deliveryBatch,
            String dishName,
            String dishCategory,
            String description,
            String imageUrl,
            Integer weightG,
            Integer kcal,
            BigDecimal proteinG,
            BigDecimal points,
            List<String> allergens,
            String storageMethod,
            String reheatMethod) {
    }
}
