package com.sancan.meal.plan.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 用户档案（小程序「我的 / 计划」基础信息）。 */
public record ProfileResp(
        Long userId,
        String gender,
        LocalDate birthDate,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal targetWeightKg,
        String activityLevel,
        String goal,
        String trainingState,
        String appetiteLevel,
        String dislikes,
        List<String> allergies,
        String proteinPref,
        String contactName,
        String contactPhone,
        String address,
        boolean onboardingCompleted,
        LocalDateTime onboardingTime) {
}
