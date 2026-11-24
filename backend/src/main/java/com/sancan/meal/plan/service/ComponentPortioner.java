package com.sancan.meal.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import com.sancan.meal.menu.service.IngredientCostCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组件化份量计算（评审 P0-1）。
 *
 * <p>菜品拆成蛋白主料 / 主食 / 蔬菜 / 油脂：蛋白主料跟蛋白目标、主食跟碳水目标、
 * 油脂跟脂肪目标，蔬菜固定。相比「按蛋白整体放大」，避免主食与油脂被同步放大导致热量超标。
 * 只在 BOM 具备组件类型与每 100g 营养时启用，否则调用方回退到旧的等比缩放。
 */
@Component
public class ComponentPortioner {

    public static final BigDecimal MIN_FACTOR = new BigDecimal("0.30");
    public static final BigDecimal MAX_FACTOR = new BigDecimal("2.50");

    private final RecipeBomMapper recipeBomMapper;
    private final IngredientMapper ingredientMapper;

    public ComponentPortioner(RecipeBomMapper recipeBomMapper, IngredientMapper ingredientMapper) {
        this.recipeBomMapper = recipeBomMapper;
        this.ingredientMapper = ingredientMapper;
    }

    public record Scaled(int weightG, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG,
                         int kcal, BigDecimal cost, boolean clamped, boolean capped) {
    }

    /** 无组件数据时返回 null，调用方回退旧的等比缩放。 */
    public Scaled scale(DishVersionDO version, BigDecimal targetProtein, BigDecimal targetCarb,
                        BigDecimal targetFat, int maxWeightG) {
        List<RecipeBomDO> bom = recipeBomMapper.selectList(new LambdaQueryWrapper<RecipeBomDO>()
                .eq(RecipeBomDO::getDishVersionId, version.getId()));
        if (bom.isEmpty()) {
            return null;
        }
        Set<Long> ids = bom.stream().map(RecipeBomDO::getIngredientId).collect(Collectors.toSet());
        Map<Long, IngredientDO> ingredients = ingredientMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(IngredientDO::getId, i -> i));

        Map<String, double[]> component = new HashMap<>();
        boolean hasProtein = false;
        boolean hasStaple = false;
        for (RecipeBomDO line : bom) {
            IngredientDO ing = ingredients.get(line.getIngredientId());
            if (ing == null || ing.getKcalPer100g() == null) {
                return null;
            }
            String type = effectiveType(line, ing);
            double grams = line.getNetWeightG() == null ? 0 : line.getNetWeightG().doubleValue();
            // 汤汁跟主料一起缩放，计入蛋白组件
            double[] totals = component.computeIfAbsent("SAUCE".equals(type) ? "PROTEIN" : type,
                    k -> new double[4]);
            totals[0] += grams / 100.0 * nz(ing.getProteinPer100g());
            totals[1] += grams / 100.0 * nz(ing.getCarbPer100g());
            totals[2] += grams / 100.0 * nz(ing.getFatPer100g());
            totals[3] += grams / 100.0 * nz(ing.getKcalPer100g());
            hasProtein |= "PROTEIN".equals(type);
            hasStaple |= "STAPLE".equals(type);
        }
        if (!hasProtein || !hasStaple) {
            return null;
        }

        double[] proteinComp = component.getOrDefault("PROTEIN", new double[4]);
        double[] stapleComp = component.getOrDefault("STAPLE", new double[4]);
        double[] fatComp = component.getOrDefault("FAT", new double[4]);

        double[] solved = solve3(proteinComp, stapleComp,
                component.getOrDefault("FAT", new double[4]),
                component.getOrDefault("VEGETABLE", new double[4]),
                targetProtein.doubleValue(), targetCarb.doubleValue(), targetFat.doubleValue());
        double fp = clamp(solved[0]);
        double fc = clamp(solved[1]);
        double ff = clamp(solved[2]);
        boolean clamped = solved[0] != fp || solved[1] != fc || solved[2] != ff;

        // 单餐重量按熟重：配方行有熟重用熟重，否则按 版本净重 ÷ 配方生重合计 等比折算。
        // 之前直接加生重，米饭这类生熟差 2.5 倍的组件会让用户看到的克数和实际出品对不上。
        double rawTotal = bom.stream().mapToDouble(l -> l.getNetWeightG() == null ? 0 : l.getNetWeightG().doubleValue()).sum();
        boolean allCooked = bom.stream().allMatch(l -> l.getCookedWeightG() != null && l.getCookedWeightG().signum() > 0);
        double cookedRatio = version.getNetWeightG() != null && version.getNetWeightG() > 0 && rawTotal > 0
                ? version.getNetWeightG() / rawTotal : 1.0;

        double weight = 0;
        double protein = 0, carb = 0, fat = 0, kcal = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (RecipeBomDO line : bom) {
            IngredientDO ing = ingredients.get(line.getIngredientId());
            String type = effectiveType(line, ing);
            double factor = switch (type) {
                case "PROTEIN", "SAUCE" -> fp;
                case "STAPLE" -> fc;
                case "FAT" -> ff;
                default -> 1.0;
            };
            double grams = (line.getNetWeightG() == null ? 0 : line.getNetWeightG().doubleValue()) * factor;
            double cooked = allCooked ? line.getCookedWeightG().doubleValue() * factor : grams * cookedRatio;
            weight += cooked;
            protein += grams / 100.0 * nz(ing.getProteinPer100g());
            carb += grams / 100.0 * nz(ing.getCarbPer100g());
            fat += grams / 100.0 * nz(ing.getFatPer100g());
            kcal += grams / 100.0 * nz(ing.getKcalPer100g());
            cost = cost.add(IngredientCostCalculator.cost(ing, BigDecimal.valueOf(grams)));
        }

        boolean capped = maxWeightG > 0 && weight > maxWeightG;
        if (capped) {
            double ratio = maxWeightG / weight;
            weight *= ratio;
            protein *= ratio;
            carb *= ratio;
            fat *= ratio;
            kcal *= ratio;
            cost = cost.multiply(BigDecimal.valueOf(ratio)).setScale(4, RoundingMode.HALF_UP);
        }

        return new Scaled(
                (int) Math.round(weight),
                BigDecimal.valueOf(protein).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(carb).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fat).setScale(2, RoundingMode.HALF_UP),
                (int) Math.round(kcal), cost, clamped, capped);
    }

    /**
     * 解三元一次方程，同时满足蛋白、碳水、脂肪目标：
     * P: a1*fp + b1*fc + c1*ff = 目标 - 蔬菜
     * 其余两行同理。无解时退回按蛋白/碳水二元解。
     */
    private double[] solve3(double[] proteinComp, double[] stapleComp, double[] fatComp,
                            double[] veg, double targetP, double targetC, double targetF) {
        double[][] m = {
                {proteinComp[0], stapleComp[0], fatComp[0], targetP - veg[0]},
                {proteinComp[1], stapleComp[1], fatComp[1], targetC - veg[1]},
                {proteinComp[2], stapleComp[2], fatComp[2], targetF - veg[2]}
        };
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(m[pivot][col]) < 1e-9) {
                return new double[]{factor(targetP, proteinComp[0]), factor(targetC, stapleComp[1]), 1.0};
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            for (int row = 0; row < 3; row++) {
                if (row == col) {
                    continue;
                }
                double ratio = m[row][col] / m[col][col];
                for (int k = col; k < 4; k++) {
                    m[row][k] -= ratio * m[col][k];
                }
            }
        }
        return new double[]{m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2]};
    }

    private double clamp(double value) {
        return Math.max(MIN_FACTOR.doubleValue(), Math.min(MAX_FACTOR.doubleValue(), value));
    }

    private double factor(double target, double base) {
        if (base <= 0) {
            return 1.0;
        }
        return Math.max(MIN_FACTOR.doubleValue(),
                Math.min(MAX_FACTOR.doubleValue(), target / base));
    }

    private double clampPlain(double target, double base) {
        return base <= 0 ? 1.0 : target / base;
    }

    private double nz(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    /** 配方行指定了组件类型时以配方行为准（做菜记录同步写入），否则沿用食材上的类型。 */
    private String effectiveType(RecipeBomDO line, IngredientDO ing) {
        if (line.getComponentType() != null && !line.getComponentType().isBlank()) {
            return normalize(line.getComponentType());
        }
        String type = normalize(ing.getComponentType());
        // 食材本身不会被标成汤汁；只有配方行可以
        return "SAUCE".equals(type) ? "OTHER" : type;
    }

    private String normalize(String type) {
        if (type == null || type.isBlank()) {
            return "OTHER";
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "PROTEIN", "SAUCE", "STAPLE", "VEGETABLE", "FAT" -> upper;
            default -> "OTHER";
        };
    }
}
