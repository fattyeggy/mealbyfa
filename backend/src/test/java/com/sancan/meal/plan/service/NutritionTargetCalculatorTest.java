package com.sancan.meal.plan.service;

import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 目标营养估算测试。年龄用「当前日期 − 30 年」构造，保证长期可重复。
 * 口径见 {@link NutritionTargetCalculator} 类注释，属初始假设，非医学处方。
 */
class NutritionTargetCalculatorTest {

    private final NutritionTargetCalculator calculator = new NutritionTargetCalculator();

    private UserProfileDO profile(String gender, double weight, String activity,
                                  String goal, String appetite) {
        UserProfileDO profile = new UserProfileDO();
        profile.setGender(gender);
        profile.setBirthDate(LocalDate.now().minusYears(30));
        profile.setHeightCm(new BigDecimal("175"));
        profile.setWeightKg(BigDecimal.valueOf(weight));
        profile.setActivityLevel(activity);
        profile.setGoal(goal);
        profile.setAppetiteLevel(appetite);
        return profile;
    }

    @Test
    @DisplayName("男性 175cm 75kg 中等活动 维持：蛋白 112.5g，热量约 2633kcal")
    void maintainCase() {
        var target = calculator.calculate(profile("MALE", 75, "MODERATE", "MAINTAIN", "NORMAL"));
        assertThat(target.dailyProteinG()).isEqualByComparingTo(new BigDecimal("112.50"));
        // BMR 1698.75 × 1.55 = 2633.06
        assertThat(target.dailyKcal()).isEqualTo(2633);
    }

    @Test
    @DisplayName("减脂目标会降低热量并提高每公斤蛋白")
    void loseCase() {
        var target = calculator.calculate(profile("MALE", 75, "MODERATE", "LOSE", "NORMAL"));
        assertThat(target.dailyProteinG()).isEqualByComparingTo(new BigDecimal("135.00"));
        assertThat(target.dailyKcal()).isEqualTo(2233);
    }

    @Test
    @DisplayName("女性基础代谢低于男性，同参数下蛋白与热量都更低")
    void femaleLowerThanMale() {
        var male = calculator.calculate(profile("MALE", 60, "LIGHT", "MAINTAIN", "NORMAL"));
        var female = calculator.calculate(profile("FEMALE", 60, "LIGHT", "MAINTAIN", "NORMAL"));
        assertThat(female.dailyKcal()).isLessThan(male.dailyKcal());
        assertThat(female.dailyProteinG()).isEqualByComparingTo(new BigDecimal("90.00")); // 60 × 1.5
    }

    @Test
    @DisplayName("目标蛋白质不再夹取：越界交给复核流程处理（P4.6 §4）")
    void proteinIsNotClamped() {
        var low = calculator.calculate(profile("FEMALE", 40, "SEDENTARY", "LOSE", "NORMAL"));
        assertThat(low.dailyProteinG()).isEqualByComparingTo(new BigDecimal("72.00"));

        var high = calculator.calculate(profile("MALE", 120, "HIGH", "GAIN", "LARGE"));
        // 120 × 1.8 × 1.1 = 237.6，不再被截断成 220
        assertThat(high.dailyProteinG()).isEqualByComparingTo(new BigDecimal("237.60"));
    }

    @Test
    @DisplayName("三餐按 25% / 40% / 35% 分配目标蛋白质")
    void mealSplit() {
        BigDecimal daily = new BigDecimal("120.00");
        assertThat(calculator.mealProtein(daily, "BREAKFAST")).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(calculator.mealProtein(daily, "LUNCH")).isEqualByComparingTo(new BigDecimal("48.00"));
        assertThat(calculator.mealProtein(daily, "DINNER")).isEqualByComparingTo(new BigDecimal("42.00"));
        assertThat(calculator.share("BREAKFAST") + calculator.share("LUNCH") + calculator.share("DINNER"))
                .isEqualTo(1.0);
    }
}
