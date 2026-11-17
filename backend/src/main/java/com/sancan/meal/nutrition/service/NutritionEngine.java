package com.sancan.meal.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营养目标计算引擎（按《营养餐 Onboarding 建档与饮食计划 · 开发说明》第 4–7 节）。
 *
 * <p>无状态纯函数：输入档案 + 配置，输出方案或拦截结果。系数全部来自配置，
 * 便于按配置版本追溯与调参。
 */
public final class NutritionEngine {

    private NutritionEngine() {
    }

    /** 建档输入。 */
    public record Input(
            String goal,            // cut / maintain / bulk
            String sex,             // male / female
            int age,
            double heightCm,
            double weightKg,
            Double bodyFatPct,      // 可空
            String dailyActivity,   // sedentary / light / active
            int workoutsPerWeek,
            int workoutMinutes,
            String workoutIntensity,// low / medium / high（次数为 0 时忽略）
            Set<String> healthFlags) {
    }

    /** 目标参数。 */
    public record GoalParams(double kcalFactor, double proteinPerKg, double fatPerKg,
                             double proteinMin, double proteinMax) {
    }

    /** 配置 v1。 */
    public record Config(
            String version,
            Map<String, Double> baseActivity,
            double exerciseAdd0to59,
            double exerciseAdd60to179,
            double exerciseAdd180to359,
            double exerciseAdd360plus,
            Map<String, Double> intensity,
            Map<String, GoalParams> goals,
            int kcalFloorFemale,
            int kcalFloorMale,
            List<Integer> tiers,
            double proteinCapPerKg,
            double fatFloorPerKg,
            int carbFloor) {
    }

    /** 计算结果。 */
    public record Result(
            boolean blocked,
            String blockReason,
            String action,
            double bmr,
            double activityFactor,
            double tdee,
            int kcal,
            int proteinRaw,
            int proteinTier,
            int fatG,
            int carbG,
            double bmi,
            double referenceWeightKg,
            List<String> flags,
            String expectedWeeklyChange) {

        public static Result blocked(String reason, String action) {
            return new Result(true, reason, action, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    List.of(), null);
        }
    }

    public static Config defaultConfig() {
        return new Config(
                "v1",
                Map.of("sedentary", 1.2, "light", 1.3, "active", 1.5),
                0, 0.175, 0.35, 0.525,
                Map.of("low", 0.8, "medium", 1.0, "high", 1.2),
                Map.of(
                        "cut", new GoalParams(0.80, 2.0, 0.8, 1.8, 2.2),
                        "maintain", new GoalParams(1.00, 1.6, 0.9, 1.4, 1.8),
                        "bulk", new GoalParams(1.10, 2.0, 0.9, 1.6, 2.2)),
                1200, 1500,
                List.of(80, 100, 130, 160, 190),
                2.2, 0.6, 100);
    }

    public static Result calculate(Input in, Config cfg) {
        // 第 6 节：计算前拦截
        String block = checkBlock(in);
        if (block != null) {
            return Result.blocked(block, "manual_consult");
        }

        double heightM = in.heightCm() / 100.0;
        double bmi = in.weightKg() / (heightM * heightM);

        double bmr;
        if (in.bodyFatPct() != null && in.bodyFatPct() > 0) {
            bmr = 370 + 21.6 * in.weightKg() * (1 - in.bodyFatPct() / 100.0);
        } else {
            bmr = 10 * in.weightKg() + 6.25 * in.heightCm() - 5 * in.age()
                    + ("male".equals(in.sex()) ? 5 : -161);
        }

        double weeklyMinutes = (double) in.workoutsPerWeek() * in.workoutMinutes();
        double add = weeklyMinutes < 60 ? cfg.exerciseAdd0to59()
                : weeklyMinutes < 180 ? cfg.exerciseAdd60to179()
                : weeklyMinutes < 360 ? cfg.exerciseAdd180to359()
                : cfg.exerciseAdd360plus();
        double intensity = in.workoutsPerWeek() <= 0 ? 1.0
                : cfg.intensity().getOrDefault(in.workoutIntensity(), 1.0);
        double af = Math.min(1.9, cfg.baseActivity().getOrDefault(in.dailyActivity(), 1.2) + add * intensity);

        double tdee = bmr * af;
        GoalParams goal = cfg.goals().get(in.goal());

        double kcal = tdee * goal.kcalFactor();
        int floor = "female".equals(in.sex()) ? cfg.kcalFloorFemale() : cfg.kcalFloorMale();
        List<String> flags = new ArrayList<>();
        if (kcal < floor) {
            kcal = floor;
            flags.add("kcal_floor_applied");
        }
        if ("bulk".equals(in.goal())) {
            kcal = Math.min(kcal, tdee + 500);
        }
        int kcalRounded = (int) (Math.round(kcal / 10.0) * 10);

        double refWeight = bmi >= 28 ? 24 * heightM * heightM : in.weightKg();
        // 蛋白系数按运动强度区分：低 −0.2、中 0、高 +0.2；不运动按低强度
        double proteinPerKg = goal.proteinPerKg();
        String intensityLevel = in.workoutsPerWeek() <= 0 ? "medium"
                : (in.workoutIntensity() == null ? "medium" : in.workoutIntensity());
        proteinPerKg += switch (intensityLevel) {
            case "high" -> 0.2;
            case "low" -> -0.2;
            default -> 0;
        };
        proteinPerKg = Math.max(goal.proteinMin(), Math.min(goal.proteinMax(), proteinPerKg));
        if (in.healthFlags() != null && in.healthFlags().contains("gout")) {
            proteinPerKg = Math.min(proteinPerKg, 1.6);
        }
        int proteinRaw = (int) Math.round(refWeight * proteinPerKg);
        proteinRaw = (int) Math.min(proteinRaw, Math.round(refWeight * cfg.proteinCapPerKg()));
        int fat = (int) Math.round(refWeight * goal.fatPerKg());
        fat = (int) Math.max(fat, Math.round(refWeight * cfg.fatFloorPerKg()));

        int tier = nearestTier(proteinRaw, cfg.tiers());
        int carb = carbOf(kcalRounded, tier, fat);

        // 碳水低于 100g：先降脂肪到下限，再降蛋白到目标范围下限
        if (carb < cfg.carbFloor()) {
            int fatFloor = (int) Math.round(refWeight * cfg.fatFloorPerKg());
            if (fat > fatFloor) {
                fat = fatFloor;
                carb = carbOf(kcalRounded, tier, fat);
                flags.add("fat_reduced_for_carb");
            }
            if (carb < cfg.carbFloor()) {
                tier = nearestTier((int) Math.round(refWeight * goal.proteinMin()), cfg.tiers());
                carb = carbOf(kcalRounded, tier, fat);
                flags.add("protein_reduced_for_carb");
            }
            if (carb < cfg.carbFloor()) {
                flags.add("low_carb");
            }
        }

        return new Result(false, null, null,
                round1(bmr), round3(af), round1(tdee), kcalRounded,
                proteinRaw, tier, fat, carb,
                round1(bmi), round1(refWeight), flags,
                expectedChange(in.goal(), in.weightKg()));
    }

    /** 第 6 节拦截规则。 */
    private static String checkBlock(Input in) {
        if (in.age() < 18) {
            return "under_18";
        }
        if (in.age() > 65) {
            return "over_65";
        }
        Set<String> flags = in.healthFlags() == null ? Set.of() : in.healthFlags();
        for (String hard : List.of("pregnant", "breastfeeding", "kidney_disease",
                "diabetes", "eating_disorder_history")) {
            if (flags.contains(hard)) {
                return hard;
            }
        }
        double bmi = in.weightKg() / Math.pow(in.heightCm() / 100.0, 2);
        if ("cut".equals(in.goal()) && bmi < 18.5) {
            return "cut_bmi_too_low";
        }
        return null;
    }

    private static int nearestTier(int protein, List<Integer> tiers) {
        int best = tiers.get(0);
        int bestDiff = Integer.MAX_VALUE;
        for (int tier : tiers) {
            int diff = Math.abs(protein - tier);
            // 差值相同时取较低档：只有严格更小才替换
            if (diff < bestDiff) {
                bestDiff = diff;
                best = tier;
            }
        }
        return best;
    }

    private static int carbOf(int kcal, int protein, int fat) {
        BigDecimal value = BigDecimal.valueOf(kcal - protein * 4.0 - fat * 9.0)
                .divide(BigDecimal.valueOf(4), 0, RoundingMode.HALF_DOWN);
        return Math.max(0, value.intValue());
    }

    private static String expectedChange(String goal, double weightKg) {
        if ("cut".equals(goal)) {
            return String.format("每周约 -%.1f ~ -%.1f kg（体重的 0.5%%–1.0%%）",
                    weightKg * 0.005, weightKg * 0.01);
        }
        if ("bulk".equals(goal)) {
            return String.format("每周约 +%.1f ~ +%.1f kg（体重的 0.1%%–0.25%%）",
                    weightKg * 0.001, weightKg * 0.0025);
        }
        return String.format("每周约 ±%.1f kg（体重的 0.25%%）", weightKg * 0.0025);
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
