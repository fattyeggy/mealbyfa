package com.sancan.meal.cook.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 做菜记录的计算结果，只读，不落库。
 *
 * @param blockers   有任何一条就不能同步到菜品
 * @param warnings   提醒，不阻止同步
 * @param parts      每个出品组件：生重、熟重、出成率、每 100g 熟重营养
 * @param standard   标准一份
 * @param tierTables 按蛋白档位的出品表，每个餐次一张
 * @param weightRows 按成品总重的出品表
 * @param bom        同步时写入菜品版本的配方（标准一份所需可食生重）
 */
public record CookCalculation(
        List<String> blockers,
        List<String> warnings,
        List<PartResult> parts,
        Plate standard,
        List<TierTable> tierTables,
        List<Plate> weightRows,
        List<BomLine> bom) {

    public record Nutrients(int kcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG) {
    }

    public record PartResult(
            String key,
            String name,
            String role,
            BigDecimal rawG,
            BigDecimal cookedG,
            /** 熟重 ÷ 生重，如 0.72 表示煮完剩 72%。 */
            BigDecimal yieldRate,
            Nutrients per100g,
            Nutrients batchTotal,
            /** 标准一份里这个组件的克数。 */
            BigDecimal standardG) {
    }

    /** 一份出品：放多少克哪个组件，以及这一份的营养。 */
    public record Plate(
            String label,
            /** 按档位时：这一餐的蛋白目标；按克重时为空。 */
            BigDecimal targetProteinG,
            /** 按克重时：目标成品总重；按档位时为空。 */
            Integer targetWeightG,
            /** 主料（含汤汁）相对标准一份的倍数；按克重时是整份倍数。 */
            BigDecimal factor,
            /** 倍数超出 0.30–2.50，与系统配餐的限制一致，需要人工看一下。 */
            boolean clamped,
            List<PlatePart> parts,
            int weightG,
            Nutrients nutrients) {
    }

    public record PlatePart(String key, String name, String role, int grams) {
    }

    public record TierTable(String mealType, String mealLabel, BigDecimal share, List<Plate> rows) {
    }

    /** rawG：标准一份所需可食生重；cookedG：这些生料做熟后在标准一份里的重量。 */
    public record BomLine(Long ingredientId, String name, String componentType, BigDecimal rawG,
                          BigDecimal cookedG, String note) {
    }
}
