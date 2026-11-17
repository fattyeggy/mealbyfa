package com.sancan.meal.menu.service;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 食材成本计算（手册 §10「食材成本规则」）。
 *
 * <pre>
 * 每份食材成本 = 采购单价 × 可食生重 ÷ 可食率
 * 采购与使用单位不同时，先按 conversionRate 折算到使用单位
 * </pre>
 *
 * 例：鸡胸肉 26.00 元/kg，可食率 90%，每份可食生重 150g
 * → 26.00 × 0.15 ÷ 0.9 = 4.33 元。
 */
public final class IngredientCostCalculator {

    /** 使用单位到克的换算因子。BOM 重量统一以克存储。 */
    private static final Map<String, BigDecimal> GRAM_FACTORS = Map.of(
            "g", BigDecimal.ONE,
            "kg", BigDecimal.valueOf(1000),
            "斤", BigDecimal.valueOf(500),
            "两", BigDecimal.valueOf(50));

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final int COST_SCALE = 4;

    private IngredientCostCalculator() {
    }

    /** 使用单位折克因子；不支持的计量单位直接报错，避免静默算错成本。 */
    public static BigDecimal gramFactor(String usageUnit) {
        BigDecimal factor = usageUnit == null ? null : GRAM_FACTORS.get(usageUnit.trim().toLowerCase());
        if (factor == null) {
            throw new ServiceException(ErrorCode.INGREDIENT_UNIT_UNCONVERTIBLE,
                    "不支持的使用单位：" + usageUnit + "（仅支持 g/kg/斤/两）");
        }
        return factor;
    }

    /** 折算到「元 / 使用单位」。 */
    public static BigDecimal unitPricePerUsageUnit(IngredientDO ingredient) {
        requireValid(ingredient);
        return ingredient.getMovingAvgPrice()
                .divide(ingredient.getConversionRate(), MC)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** 单份该食材成本。 */
    public static BigDecimal cost(IngredientDO ingredient, BigDecimal netWeightG) {
        if (netWeightG == null || netWeightG.signum() < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "BOM 重量必须为非负数");
        }
        requireValid(ingredient);
        BigDecimal perUsageUnit = unitPricePerUsageUnit(ingredient);
        BigDecimal gramFactor = gramFactor(ingredient.getUsageUnit());
        // 元/使用单位 × (克 ÷ 克/使用单位) ÷ 可食率
        return perUsageUnit
                .multiply(netWeightG.divide(gramFactor, MC), MC)
                .divide(ingredient.getEdibleRate(), MC)
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    /** 一份菜品的食材成本 = 各 BOM 行成本之和。 */
    public static BigDecimal dishCost(List<RecipeBomDO> bomLines, Map<Long, IngredientDO> ingredientMap) {
        BigDecimal total = BigDecimal.ZERO;
        for (RecipeBomDO line : bomLines) {
            IngredientDO ingredient = ingredientMap.get(line.getIngredientId());
            if (ingredient == null) {
                throw new ServiceException(ErrorCode.INGREDIENT_NOT_FOUND,
                        "BOM 引用的食材不存在：" + line.getIngredientId());
            }
            total = total.add(cost(ingredient, line.getNetWeightG()));
        }
        return total.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private static void requireValid(IngredientDO ingredient) {
        if (ingredient.getConversionRate() == null || ingredient.getConversionRate().signum() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "食材换算率必须大于 0：" + ingredient.getName());
        }
        if (ingredient.getEdibleRate() == null
                || ingredient.getEdibleRate().signum() <= 0
                || ingredient.getEdibleRate().compareTo(BigDecimal.ONE) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "食材可食率必须在 (0,1] 区间：" + ingredient.getName());
        }
        if (ingredient.getMovingAvgPrice() == null || ingredient.getMovingAvgPrice().signum() < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "食材价格不能为负：" + ingredient.getName());
        }
    }
}
