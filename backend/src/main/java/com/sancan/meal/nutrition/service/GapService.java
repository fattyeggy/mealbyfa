package com.sancan.meal.nutrition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.plan.dal.dataobject.PlanItemDO;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.dal.mapper.PlanItemMapper;
import com.sancan.meal.plan.dal.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日缺口（双向）：不足超过 5% 显示「还差」，超出超过 10% 显示「已超出」，其间视为达标。
 */
@Service
public class GapService {

    private static final double SHORT_TOLERANCE = 0.05;
    private static final double OVER_TOLERANCE = 0.10;

    private final UserProfileMapper profileMapper;
    private final PlanItemMapper planItemMapper;

    public GapService(UserProfileMapper profileMapper, PlanItemMapper planItemMapper) {
        this.profileMapper = profileMapper;
        this.planItemMapper = planItemMapper;
    }

    /** status: SHORT / OVER / OK。 */
    public record Line(String nutrient, String label, int targetG, int actualG,
                       int gapG, int excessG, String status, boolean achieved, String volumeText) {
    }

    public record Resp(LocalDate serviceDate, int targetKcal, int actualKcal,
                       String kcalStatus, int kcalDiff,
                       List<Line> lines, boolean allAchieved) {
    }

    public Resp compute(Long userId, LocalDate date) {
        UserProfileDO profile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserProfileDO>().eq(UserProfileDO::getUserId, userId));
        if (profile == null) {
            return new Resp(date, 0, 0, "OK", 0, List.of(), true);
        }
        Set<String> meals = parseMeals(profile.getOrderedMeals());
        List<PlanItemDO> items = planItemMapper.selectList(new LambdaQueryWrapper<PlanItemDO>()
                .eq(PlanItemDO::getUserId, userId)
                .eq(PlanItemDO::getServiceDate, date));
        List<PlanItemDO> ordered = items.stream()
                .filter(i -> meals.contains(i.getMealType()))
                .toList();

        int actualProtein = sum(ordered, PlanItemDO::getProteinG);
        int actualCarb = sum(ordered, PlanItemDO::getCarbG);
        int actualFat = sum(ordered, PlanItemDO::getFatG);
        int actualKcal = ordered.stream().mapToInt(i -> i.getKcal() == null ? 0 : i.getKcal()).sum();

        int pTarget = profile.getTargetProteinTier() == null ? 0 : profile.getTargetProteinTier();
        int cTarget = profile.getTargetCarbG() == null ? 0 : profile.getTargetCarbG();
        int fTarget = profile.getTargetFatG() == null ? 0 : profile.getTargetFatG();
        int kTarget = profile.getTargetKcal() == null ? 0 : profile.getTargetKcal();

        List<Line> lines = new ArrayList<>();
        lines.add(line("protein", "蛋白质", pTarget, actualProtein, 25, "掌心"));
        lines.add(line("carb", "碳水", cTarget, actualCarb, 40, "拳米饭"));
        lines.add(line("fat", "脂肪", fTarget, actualFat, 14, "汤匙油"));

        boolean all = lines.stream().allMatch(Line::achieved);
        String kcalStatus;
        int kcalDiff;
        if (kTarget <= 0) {
            kcalStatus = "OK";
            kcalDiff = 0;
        } else if (actualKcal < kTarget * (1 - SHORT_TOLERANCE)) {
            kcalStatus = "SHORT";
            kcalDiff = kTarget - actualKcal;
        } else if (actualKcal > kTarget * (1 + OVER_TOLERANCE)) {
            kcalStatus = "OVER";
            kcalDiff = actualKcal - kTarget;
        } else {
            kcalStatus = "OK";
            kcalDiff = 0;
        }
        return new Resp(date, kTarget, actualKcal, kcalStatus, kcalDiff, lines, all);
    }

    private Line line(String key, String label, int target, int actual, int perServing, String food) {
        if (target <= 0) {
            return new Line(key, label, 0, actual, 0, 0, "OK", true, "已达标");
        }
        if (actual < target * (1 - SHORT_TOLERANCE)) {
            int gap = target - actual;
            return new Line(key, label, target, actual, gap, 0, "SHORT", false,
                    "还差 " + gap + "g，约 " + volume(gap, perServing, food));
        }
        if (actual > target * (1 + OVER_TOLERANCE)) {
            int excess = actual - target;
            // 蛋白略超不用标红，按达标处理；只有碳水/脂肪超出才提示
            if ("protein".equals(key)) {
                return new Line(key, label, target, actual, 0, 0, "OK", true, "已达标");
            }
            return new Line(key, label, target, actual, 0, excess, "OVER", false,
                    "超出 " + excess + "g，约 " + volume(excess, perServing, food));
        }
        return new Line(key, label, target, actual, Math.max(target - actual, 0), 0, "OK", true, "已达标");
    }

    private String volume(int grams, int perServing, String food) {
        double servings = grams / (double) perServing;
        double rounded = Math.round(servings * 2) / 2.0;
        if (rounded < 0.5) {
            return "不到半份" + food;
        }
        String amount = rounded == Math.floor(rounded) ? String.valueOf((int) rounded)
                : String.valueOf(rounded);
        return amount + " 份" + food;
    }

    private Set<String> parseMeals(String value) {
        if (value == null || value.isBlank()) {
            return Set.of("BREAKFAST", "LUNCH", "DINNER");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toUpperCase).collect(Collectors.toSet());
    }

    private int sum(List<PlanItemDO> items, java.util.function.Function<PlanItemDO, BigDecimal> getter) {
        return items.stream().map(getter).filter(java.util.Objects::nonNull)
                .map(v -> v.setScale(0, RoundingMode.HALF_UP).intValue())
                .reduce(0, Integer::sum);
    }
}
