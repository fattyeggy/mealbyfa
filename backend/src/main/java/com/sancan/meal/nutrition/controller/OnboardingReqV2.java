package com.sancan.meal.nutrition.controller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 开发说明第 3 节：Onboarding 六步表单。 */
public record OnboardingReqV2(
        @NotBlank String goal,                 // cut / maintain / bulk
        @NotBlank String sex,                  // male / female
        @NotNull @Min(18) @Max(65) Integer age,
        @NotNull @DecimalMin("130") @DecimalMax("220") BigDecimal heightCm,
        @NotNull @DecimalMin("35") @DecimalMax("200") BigDecimal weightKg,
        @DecimalMin("5") @DecimalMax("60") BigDecimal bodyFatPct,
        @NotBlank String dailyActivity,        // sedentary / light / active
        @NotNull @Min(0) @Max(14) Integer workoutsPerWeek,
        Integer workoutMinutes,
        String workoutType,                    // strength / cardio / mixed
        String workoutIntensity,               // low / medium / high
        List<String> healthFlags,
        List<String> allergies,
        List<String> dietRestrictions,
        List<String> orderedMeals,             // breakfast / lunch / dinner
        TastePrefs tastePrefs,
        BigDecimal targetWeightKg,
        String contactName,
        String contactPhone,
        String address,
        Boolean privacyAccepted,
        LocalDate birthDate) {

    public record TastePrefs(Integer spice, String staple) {
    }
}
