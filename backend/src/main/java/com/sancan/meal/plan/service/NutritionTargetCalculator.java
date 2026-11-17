package com.sancan.meal.plan.service;

import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.enums.ActivityLevel;
import com.sancan.meal.plan.enums.AppetiteLevel;
import com.sancan.meal.plan.enums.DietGoal;
import com.sancan.meal.plan.enums.Gender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * 目标营养估算。
 *
 * <p>这是<b>规则化的初始估算</b>，不是医疗或营养处方。手册 §5 明确要求
 * 「系统生成初始计划；人工营养规则校验后进入个人首页」，因此结果仅作为计划初稿，
 * 由运营复核。产品不承诺固定减重结果（手册 §1）。
 *
 * <p>口径（均为可调整的初始假设）：
 * <ul>
 *   <li>基础代谢：Mifflin-St Jeor 公式；</li>
 *   <li>总消耗：基础代谢 × 活动系数；</li>
 *   <li>目标热量：减脂 −400 kcal、维持 0、增肌 +300 kcal；</li>
 *   <li>目标蛋白质：减脂 1.8 g/kg、维持 1.5 g/kg、增肌 1.8 g/kg，再乘食量系数；越界不夹取，交由复核流程处理；</li>
 *   <li>三餐分配：早餐 25%、午餐 40%、晚餐 35%。</li>
 * </ul>
 */
@Component
public class NutritionTargetCalculator {

    private static final int DEFAULT_AGE = 30;
    private static final double PROTEIN_PER_KG_LOSE = 1.8;
    private static final double PROTEIN_PER_KG_MAINTAIN = 1.5;
    private static final double PROTEIN_PER_KG_GAIN = 1.8;

    private static final double SHARE_BREAKFAST = 0.25;
    private static final double SHARE_LUNCH = 0.40;
    private static final double SHARE_DINNER = 0.35;

    /** 计算结果。 */
    public record Target(BigDecimal dailyProteinG, int dailyKcal) {
    }

    public Target calculate(UserProfileDO profile) {
        Gender gender = Gender.of(profile.getGender());
        DietGoal goal = DietGoal.of(profile.getGoal());
        ActivityLevel activity = ActivityLevel.of(profile.getActivityLevel());
        AppetiteLevel appetite = AppetiteLevel.of(profile.getAppetiteLevel());

        int age = profile.getBirthDate() == null ? DEFAULT_AGE
                : Math.max(14, Period.between(profile.getBirthDate(), LocalDate.now()).getYears());
        double weight = profile.getWeightKg() == null ? 65 : profile.getWeightKg().doubleValue();
        double height = profile.getHeightCm() == null ? 170 : profile.getHeightCm().doubleValue();

        double bmr = 10 * weight + 6.25 * height - 5 * age + (gender == Gender.MALE ? 5 : -161);
        double tdee = bmr * activity.getFactor();
        double kcal = switch (goal) {
            case LOSE -> tdee - 400;
            case GAIN -> tdee + 300;
            default -> tdee;
        };

        double perKg = switch (goal) {
            case LOSE -> PROTEIN_PER_KG_LOSE;
            case GAIN -> PROTEIN_PER_KG_GAIN;
            default -> PROTEIN_PER_KG_MAINTAIN;
        };
        // 不做夹取：越界由计划生成环节写入复核事项（P4.6 §4）
        double protein = weight * perKg * appetite.getFactor();

        return new Target(
                BigDecimal.valueOf(protein).setScale(2, RoundingMode.HALF_UP),
                (int) Math.round(kcal));
    }

    /** 单个餐次的目标蛋白质。 */
    public BigDecimal mealProtein(BigDecimal dailyProteinG, String mealType) {
        double share = switch (mealType) {
            case "BREAKFAST" -> SHARE_BREAKFAST;
            case "LUNCH" -> SHARE_LUNCH;
            default -> SHARE_DINNER;
        };
        return dailyProteinG.multiply(BigDecimal.valueOf(share)).setScale(2, RoundingMode.HALF_UP);
    }

    public double share(String mealType) {
        return switch (mealType) {
            case "BREAKFAST" -> SHARE_BREAKFAST;
            case "LUNCH" -> SHARE_LUNCH;
            default -> SHARE_DINNER;
        };
    }
}
