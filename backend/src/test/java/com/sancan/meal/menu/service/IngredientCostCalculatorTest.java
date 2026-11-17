package com.sancan.meal.menu.service;

import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 食材成本口径测试，基准用例取自交接手册 §10 的算例。 */
class IngredientCostCalculatorTest {

    private IngredientDO ingredient(String purchaseUnit, String usageUnit, String conversion,
                                    String edibleRate, String price) {
        IngredientDO ingredient = new IngredientDO();
        ingredient.setName("测试食材");
        ingredient.setPurchaseUnit(purchaseUnit);
        ingredient.setUsageUnit(usageUnit);
        ingredient.setConversionRate(new BigDecimal(conversion));
        ingredient.setEdibleRate(new BigDecimal(edibleRate));
        ingredient.setMovingAvgPrice(new BigDecimal(price));
        return ingredient;
    }

    @Test
    @DisplayName("手册算例：26 元/kg、可食率 90%、可食生重 150g → 4.33 元")
    void manualExample() {
        IngredientDO chicken = ingredient("kg", "kg", "1", "0.9", "26.00");
        BigDecimal cost = IngredientCostCalculator.cost(chicken, new BigDecimal("150"));
        assertThat(cost).isEqualByComparingTo(new BigDecimal("4.3333"));
    }

    @Test
    @DisplayName("采购单位与使用单位不同：按换算率折算（1 箱 = 10 kg）")
    void unitConversion() {
        IngredientDO boxed = ingredient("箱", "kg", "10", "1", "260.00");
        // 26 元/kg × 0.15kg = 3.90
        assertThat(IngredientCostCalculator.cost(boxed, new BigDecimal("150")))
                .isEqualByComparingTo(new BigDecimal("3.9000"));
    }

    @Test
    @DisplayName("以克为使用单位时无需再折算")
    void gramUsageUnit() {
        IngredientDO gram = ingredient("kg", "g", "1000", "1", "26.00");
        assertThat(IngredientCostCalculator.cost(gram, new BigDecimal("150")))
                .isEqualByComparingTo(new BigDecimal("3.9000"));
    }

    @Test
    @DisplayName("可食率越低，同样可食生重的采购成本越高")
    void lowerEdibleRateCostsMore() {
        IngredientDO normal = ingredient("kg", "kg", "1", "1", "26.00");
        IngredientDO lossy = ingredient("kg", "kg", "1", "0.5", "26.00");
        BigDecimal normalCost = IngredientCostCalculator.cost(normal, new BigDecimal("150"));
        BigDecimal lossyCost = IngredientCostCalculator.cost(lossy, new BigDecimal("150"));
        assertThat(lossyCost).isGreaterThan(normalCost);
        assertThat(lossyCost).isEqualByComparingTo(new BigDecimal("7.8000"));
    }

    @Test
    @DisplayName("不支持的计量单位直接报错，避免静默算错成本")
    void unsupportedUnit() {
        IngredientDO weird = ingredient("袋", "包", "1", "1", "10.00");
        assertThatThrownBy(() -> IngredientCostCalculator.cost(weird, BigDecimal.TEN))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的使用单位");
    }

    @Test
    @DisplayName("可食率为 0 视为数据错误")
    void zeroEdibleRate() {
        IngredientDO broken = ingredient("kg", "kg", "1", "0.0001", "26.00");
        broken.setEdibleRate(BigDecimal.ZERO);
        assertThatThrownBy(() -> IngredientCostCalculator.cost(broken, BigDecimal.TEN))
                .isInstanceOf(ServiceException.class);
    }
}
