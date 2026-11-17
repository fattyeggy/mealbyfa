package com.sancan.meal.pricing;

import com.sancan.meal.pricing.dal.dataobject.PricingVersionDO;
import com.sancan.meal.pricing.service.DailyPricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计价规则 V1.0 验收用例 P01–P07、P11。
 * 基准参数直接来自《三餐私教计价规则与开发验收 V1.0》§4.1 / §10.2。
 */
class PricingRulesTest {

    private final DailyPricingService service = new DailyPricingService();

    private PricingVersionDO version() {
        PricingVersionDO version = new PricingVersionDO();
        version.setId(1L);
        version.setVersionNo("V1.0");
        version.setDailyBasePrice(new BigDecimal("66.00"));
        version.setIncludedProteinG(60);
        version.setExtraProteinPricePer10g(new BigDecimal("1.90"));
        version.setMinDailyPrice(new BigDecimal("65.00"));
        version.setMinTargetProteinG(80);
        version.setMaxTargetProteinG(220);
        version.setSwapCostDivisor(new BigDecimal("0.7700"));
        version.setBreakfastRatio(new BigDecimal("0.2500"));
        version.setLunchRatio(new BigDecimal("0.4000"));
        return version;
    }

    private DailyPricingService.Quote quote(int protein) {
        return service.quote(version(), protein, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("P01 标准购价表：80/100/120/135/150/180/200/220g 全部与文档一致")
    void p01PriceTable() {
        int[][] cases = {{80, 70}, {100, 74}, {120, 77}, {135, 80}, {150, 83}, {180, 89}, {200, 93}, {220, 96}};
        for (int[] c : cases) {
            DailyPricingService.Quote q = quote(c[0]);
            assertThat(q.dailyPrice()).as("P=" + c[0] + "g").isEqualTo(c[1]);
        }
        // 未取整价格逐行核对（文档 §4.1 第二列）
        assertThat(quote(80).rawReferencePrice()).isEqualByComparingTo("69.80");
        assertThat(quote(135).rawReferencePrice()).isEqualByComparingTo("80.25");
        assertThat(quote(220).rawReferencePrice()).isEqualByComparingTo("96.40");
    }

    @Test
    @DisplayName("P02 三餐拆分保留两位小数，且始终等于日价")
    void p02MealSplit() {
        for (int protein = 80; protein <= 220; protein += 5) {
            DailyPricingService.Quote q = quote(protein);
            BigDecimal sum = q.breakfastPrice().add(q.lunchPrice()).add(q.dinnerPrice());
            assertThat(sum).as("P=" + protein).isEqualByComparingTo(BigDecimal.valueOf(q.dailyPrice()));
        }
        DailyPricingService.Quote q77 = quote(120);
        assertThat(q77.breakfastPrice()).isEqualByComparingTo("19.25");
        assertThat(q77.lunchPrice()).isEqualByComparingTo("30.80");
        assertThat(q77.dinnerPrice()).isEqualByComparingTo("26.95");

        DailyPricingService.Quote q83 = quote(150);
        assertThat(q83.breakfastPrice()).isEqualByComparingTo("20.75");
        assertThat(q83.lunchPrice()).isEqualByComparingTo("33.20");
        assertThat(q83.dinnerPrice()).isEqualByComparingTo("29.05");
    }

    @Test
    @DisplayName("P03 150g 无替换为 83 元，不得出现旧实现的 95.50 或 88.05")
    void p03NoLegacyPrice() {
        DailyPricingService.Quote q = quote(150);
        assertThat(q.dailyPrice()).isEqualTo(83);
        assertThat(q.rawReferencePrice()).isEqualByComparingTo("83.10");
        assertThat(BigDecimal.valueOf(q.dailyPrice())).isNotEqualByComparingTo("95.50");
        assertThat(BigDecimal.valueOf(q.dailyPrice())).isNotEqualByComparingTo("88.05");
    }

    @Test
    @DisplayName("P04 贵价替换：变动成本 20→26，150g 日价 83.10 + 7.7922 → 91 元")
    void p04ExpensiveSwap() {
        PricingVersionDO version = version();
        BigDecimal difference = service.swapDifference(version,
                new BigDecimal("20.00"), new BigDecimal("26.00"));
        assertThat(difference).isEqualByComparingTo("7.7922");

        DailyPricingService.Quote q = service.quote(version, 150, difference);
        assertThat(q.rawAdjustedPrice()).isEqualByComparingTo("90.8922");
        assertThat(q.dailyPrice()).isEqualTo(91);
    }

    @Test
    @DisplayName("P05 便宜替换必须产生负差价，不得强制为 0")
    void p05CheapSwapNegative() {
        PricingVersionDO version = version();
        BigDecimal difference = service.swapDifference(version,
                new BigDecimal("26.00"), new BigDecimal("20.00"));
        assertThat(difference.signum()).isNegative();
        DailyPricingService.Quote q = service.quote(version, 150, difference);
        assertThat(q.dailyPrice()).isLessThan(83);
    }

    @Test
    @DisplayName("P07 固定成本变化不影响替换差价")
    void p07FixedCostIrrelevant() {
        PricingVersionDO a = version();
        PricingVersionDO b = version();
        b.setDailyBasePrice(new BigDecimal("70.00"));
        BigDecimal diffA = service.swapDifference(a, new BigDecimal("20.00"), new BigDecimal("26.00"));
        BigDecimal diffB = service.swapDifference(b, new BigDecimal("20.00"), new BigDecimal("26.00"));
        assertThat(diffA).isEqualByComparingTo(diffB);
    }

    @Test
    @DisplayName("P11 蛋白超出范围时标记人工复核，不静默改价")
    void p11ProteinOutOfRangeMarksReview() {
        DailyPricingService.Quote q = quote(40);
        assertThat(q.needsReview()).isTrue();
        assertThat(q.notes()).anyMatch(n -> n.contains("超出"));
    }

    @Test
    @DisplayName("成本缺失时返回 null，调用方必须拒绝而不是按 0 差价处理")
    void missingCostReturnsNull() {
        assertThat(service.swapDifference(version(), null, BigDecimal.TEN)).isNull();
        assertThat(service.swapDifference(version(), BigDecimal.TEN, null)).isNull();
    }
}
