package com.sancan.meal.plan.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Onboarding 建档入参（手册 §5：基础数据、目标、训练、食量、忌口、过敏、蛋白偏好）。 */
public record OnboardingReq(
        @NotNull(message = "请选择性别") String gender,
        @NotNull(message = "请填写出生日期") LocalDate birthDate,
        @NotNull(message = "请填写身高")
        @DecimalMin(value = "120", message = "身高需在 120-220cm")
        @DecimalMax(value = "220", message = "身高需在 120-220cm") BigDecimal heightCm,
        @NotNull(message = "请填写体重")
        @DecimalMin(value = "30", message = "体重需在 30-250kg")
        @DecimalMax(value = "250", message = "体重需在 30-250kg") BigDecimal weightKg,
        BigDecimal targetWeightKg,
        @NotNull(message = "请选择活动水平") String activityLevel,
        @NotNull(message = "请选择目标") String goal,
        String trainingState,
        @NotNull(message = "请选择食量") String appetiteLevel,
        String dislikes,
        String allergies,
        String proteinPref,
        String contactName,
        String contactPhone,
        String address) {
}
