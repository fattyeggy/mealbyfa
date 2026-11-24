package com.sancan.meal.cook;

import com.sancan.meal.cook.controller.dto.CookContent;
import com.sancan.meal.cook.service.CookCalculation;
import com.sancan.meal.cook.service.CookCalculator;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 示例批次（数字均可手算）：
 * <pre>
 * 肉   = 鸡胸 1000g + 油 20g       → 熟重 750g，标准一份 150g
 * 汤汁 = 酱油 50g + 糖 30g         → 熟重 200g，标准一份 40g
 * 米饭 = 大米 400g                 → 熟重 1000g，标准一份 200g
 * 蔬菜 = 西兰花 300g               → 熟重 280g，标准一份 56g
 * 分 5 份，各组件标准一份都是整锅的 1/5。
 * </pre>
 */
class CookCalculatorTest {

    private static final List<CookCalculator.MealShare> MEALS = List.of(
            new CookCalculator.MealShare("LUNCH", "午餐", 0.40),
            new CookCalculator.MealShare("DINNER", "晚餐", 0.35),
            new CookCalculator.MealShare("BREAKFAST", "早餐", 0.25));
    private static final List<Integer> TIERS = List.of(80, 100, 130, 160, 190);

    private final Map<Long, IngredientDO> library = new HashMap<>();

    CookCalculatorTest() {
        put(1L, "鸡胸肉", "120", "22.5", "0", "2");
        put(2L, "植物油", "900", "0", "0", "100");
        put(3L, "酱油", "60", "8", "5", "0");
        put(4L, "白砂糖", "400", "0", "100", "0");
        put(5L, "大米", "346", "7.4", "77.9", "0.8");
        put(6L, "西兰花", "34", "2.8", "6.6", "0.4");
    }

    private void put(Long id, String name, String kcal, String p, String c, String f) {
        IngredientDO i = new IngredientDO();
        i.setId(id);
        i.setName(name);
        i.setKcalPer100g(new BigDecimal(kcal));
        i.setProteinPer100g(new BigDecimal(p));
        i.setCarbPer100g(new BigDecimal(c));
        i.setFatPer100g(new BigDecimal(f));
        library.put(id, i);
    }

    static CookContent batch() {
        return new CookContent(5,
                List.of(ing("a", 1L, "1000", "meat"), ing("b", 2L, "20", "meat"),
                        ing("c", 3L, "50", "sauce"), ing("d", 4L, "30", "sauce"),
                        ing("e", 5L, "400", "staple"), ing("f", 6L, "300", "veg")),
                List.of(part("meat", "肉", "PROTEIN", "750"), part("sauce", "汤汁", "SAUCE", "200"),
                        part("staple", "米饭", "STAPLE", "1000"), part("veg", "蔬菜", "VEGETABLE", "280")),
                List.of(new CookContent.Step("s1", "鸡胸切块腌 20 分钟", null, 1200, List.of())),
                null);
    }

    static CookContent.Ingredient ing(String key, Long id, String g, String part) {
        return new CookContent.Ingredient(key, id, null, g == null ? null : new BigDecimal(g), part, null);
    }

    static CookContent.Part part(String key, String name, String role, String cooked) {
        return new CookContent.Part(key, name, role, cooked == null ? null : new BigDecimal(cooked), null);
    }

    private CookCalculation calc(CookContent c) {
        return CookCalculator.calculate(c, library, TIERS, MEALS);
    }

    @Test
    @DisplayName("组件：生重、熟重、出成率、每 100g 熟重营养")
    void parts() {
        CookCalculation r = calc(batch());
        assertThat(r.blockers()).isEmpty();
        CookCalculation.PartResult meat = r.parts().get(0);
        assertThat(meat.rawG()).isEqualByComparingTo("1020");
        assertThat(meat.yieldRate()).isEqualByComparingTo("0.74");      // 750 / 1020
        // 鸡胸 1200 kcal + 油 180 kcal = 1380 kcal / 750g → 184 kcal/100g
        assertThat(meat.per100g().kcal()).isEqualTo(184);
        assertThat(meat.per100g().proteinG()).isEqualByComparingTo("30.0"); // 225 / 750
        assertThat(meat.standardG()).isEqualByComparingTo("150");
        CookCalculation.PartResult rice = r.parts().get(2);
        assertThat(rice.yieldRate()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("标准一份：肉 150 + 汤汁 40 + 米饭 200 + 蔬菜 56 = 446g，603 kcal")
    void standardPortion() {
        CookCalculation.Plate std = calc(batch()).standard();
        assertThat(std.weightG()).isEqualTo(446);
        assertThat(std.nutrients().kcal()).isEqualTo(603);
        assertThat(std.nutrients().proteinG()).isEqualByComparingTo("53.4");
        assertThat(std.nutrients().carbG()).isEqualByComparingTo("72.8");
        assertThat(std.nutrients().fatG()).isEqualByComparingTo("8.9");
    }

    @Test
    @DisplayName("档位表：160g 档午餐 64g 蛋白 → 肉 185g、汤汁 49g，主食蔬菜不动")
    void tierTable() {
        CookCalculation r = calc(batch());
        CookCalculation.TierTable lunch = r.tierTables().get(0);
        assertThat(lunch.mealType()).isEqualTo("LUNCH");
        CookCalculation.Plate row = lunch.rows().get(3);
        assertThat(row.targetProteinG()).isEqualByComparingTo("64.0");
        // (64 − 7.6) / 45.8 = 1.2314
        assertThat(row.factor()).isEqualByComparingTo("1.23");
        assertThat(grams(row, "meat")).isEqualTo(185);
        assertThat(grams(row, "sauce")).isEqualTo(49);
        assertThat(grams(row, "staple")).isEqualTo(200);
        assertThat(grams(row, "veg")).isEqualTo(56);
        assertThat(row.weightG()).isEqualTo(490);
        assertThat(row.nutrients().proteinG()).isEqualByComparingTo("64.1");
        assertThat(row.nutrients().kcal()).isEqualTo(674);
        assertThat(row.clamped()).isFalse();
    }

    @Test
    @DisplayName("档位表：80g 档早餐只要 20g 蛋白，倍数低于 0.30 被限制并标记")
    void tierClamped() {
        CookCalculation.Plate row = calc(batch()).tierTables().get(2).rows().get(0);
        assertThat(row.factor()).isEqualByComparingTo("0.30");
        assertThat(row.clamped()).isTrue();
        assertThat(grams(row, "meat")).isEqualTo(45);
    }

    @Test
    @DisplayName("克重表：400g 一份按标准比例缩放")
    void weightTable() {
        CookCalculation.Plate row = calc(batch()).weightRows().get(1);
        assertThat(row.targetWeightG()).isEqualTo(400);
        assertThat(grams(row, "meat")).isEqualTo(135);
        assertThat(grams(row, "sauce")).isEqualTo(36);
        assertThat(grams(row, "staple")).isEqualTo(179);
        assertThat(grams(row, "veg")).isEqualTo(50);
        assertThat(row.weightG()).isEqualTo(400);
    }

    @Test
    @DisplayName("同步配方：标准一份的可食生重 + 组件类型")
    void bom() {
        List<CookCalculation.BomLine> bom = calc(batch()).bom();
        assertThat(bom).extracting(CookCalculation.BomLine::ingredientId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(bom).extracting(CookCalculation.BomLine::componentType)
                .containsExactly("PROTEIN", "PROTEIN", "SAUCE", "SAUCE", "STAPLE", "VEGETABLE");
        assertThat(bom).extracting(l -> l.rawG().toPlainString())
                .containsExactly("200.0", "4.0", "10.0", "6.0", "80.0", "60.0");
        // 熟重按组件出成率均摊：肉 150 = 147.1 + 2.9，汤汁 40 = 25 + 15
        assertThat(bom).extracting(l -> l.cookedG().toPlainString())
                .containsExactly("147.1", "2.9", "25.0", "15.0", "200.0", "56.0");
    }

    @Test
    @DisplayName("同一种油用在肉和蔬菜里：配方里按组件分两行")
    void sameIngredientTwoParts() {
        CookContent b = batch();
        List<CookContent.Ingredient> ings = new java.util.ArrayList<>(b.ingredients());
        ings.add(ing("g", 2L, "10", "veg"));
        CookContent c = new CookContent(5, ings, b.components(), b.steps(), null);
        List<CookCalculation.BomLine> oil = calc(c).bom().stream()
                .filter(l -> l.ingredientId() == 2L).toList();
        assertThat(oil).extracting(CookCalculation.BomLine::componentType)
                .containsExactly("PROTEIN", "VEGETABLE");
    }

    @Test
    @DisplayName("没称熟重、没归组件、缺营养都会阻止同步；没称重的食材只提醒")
    void blockers() {
        IngredientDO noNutrition = new IngredientDO();
        noNutrition.setId(7L);
        noNutrition.setName("自制辣酱");
        library.put(7L, noNutrition);
        CookContent c = new CookContent(5,
                List.of(ing("a", 1L, "1000", "meat"), ing("b", 6L, "100", null),
                        ing("c", 7L, "30", "sauce"), ing("d", 5L, null, "staple")),
                List.of(part("meat", "肉", "PROTEIN", null), part("sauce", "汤汁", "SAUCE", "100"),
                        part("staple", "米饭", "STAPLE", null)),
                List.of(), null);
        CookCalculation r = calc(c);
        assertThat(r.blockers()).anyMatch(s -> s.contains("西兰花") && s.contains("出品组件"));
        assertThat(r.blockers()).anyMatch(s -> s.contains("自制辣酱") && s.contains("营养"));
        assertThat(r.blockers()).anyMatch(s -> s.contains("肉") && s.contains("熟重"));
        assertThat(r.warnings()).anyMatch(s -> s.contains("大米") && s.contains("没称重"));
    }

    @Test
    @DisplayName("填了标准一份克重时以克重为准，不按份数")
    void explicitStandard() {
        CookContent b = batch();
        List<CookContent.Part> parts = List.of(
                new CookContent.Part("meat", "肉", "PROTEIN", new BigDecimal("750"), new BigDecimal("120")),
                b.components().get(1), b.components().get(2), b.components().get(3));
        CookCalculation r = calc(new CookContent(5, b.ingredients(), parts, b.steps(), List.of(500)));
        assertThat(grams(r.standard(), "meat")).isEqualTo(120);
        assertThat(grams(r.standard(), "sauce")).isEqualTo(40);
        assertThat(r.weightRows()).hasSize(1);
    }

    private int grams(CookCalculation.Plate plate, String key) {
        return plate.parts().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().grams();
    }
}
