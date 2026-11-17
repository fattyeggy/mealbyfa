package com.sancan.meal.pricing.service;

import com.sancan.meal.pricing.dal.dataobject.PricingVersionDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 日价计算引擎（《三餐私教计价规则与开发验收 V1.0》）。
 *
 * <pre>
 * 加量蛋白     = max(0, P − 基础蛋白)
 * 未取整日价   = 基础三餐费 + 加量单价 × 加量蛋白 ÷ 10 + Σ替换差价
 * 参考日价     = max(最低日价, roundHalfUp(未取整日价, 整数元))
 * 替换差价     = (替换餐次变动成本 − 原餐次变动成本) ÷ 成本折算系数
 * 三餐展示价   = 早 25%、午 40%、晚取余额，保证三餐之和等于日价
 * </pre>
 *
 * 全程使用 BigDecimal，日价只在最后取整一次。
 */
@Component
public class DailyPricingService {

    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 计算结果。金额字段：日价为整数元，其余保留两位。 */
    public record Quote(
            Long versionId,
            String versionNo,
            int proteinG,
            BigDecimal points,
            int baseProteinG,
            int extraProteinG,
            BigDecimal basePrice,
            BigDecimal extraFee,
            BigDecimal rawReferencePrice,
            int referencePrice,
            BigDecimal swapDifference,
            BigDecimal rawAdjustedPrice,
            int dailyPrice,
            BigDecimal breakfastPrice,
            BigDecimal lunchPrice,
            BigDecimal dinnerPrice,
            boolean needsReview,
            List<String> notes) {
    }

    public Quote quote(PricingVersionDO version, int proteinG, BigDecimal swapDifference) {
        BigDecimal swap = swapDifference == null ? BigDecimal.ZERO : swapDifference;
        int baseProtein = version.getIncludedProteinG() == null ? 60 : version.getIncludedProteinG();
        int extraProtein = Math.max(0, proteinG - baseProtein);

        BigDecimal extraFee = version.getExtraProteinPricePer10g()
                .multiply(BigDecimal.valueOf(extraProtein))
                .divide(TEN, 2, RoundingMode.HALF_UP);
        BigDecimal rawReference = version.getDailyBasePrice().add(extraFee);
        int referencePrice = clampMin(roundToYuan(rawReference), version);

        BigDecimal rawAdjusted = rawReference.add(swap);
        int dailyPrice = clampMin(roundToYuan(rawAdjusted), version);

        BigDecimal breakfast = BigDecimal.valueOf(dailyPrice).multiply(version.getBreakfastRatio())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lunch = BigDecimal.valueOf(dailyPrice).multiply(version.getLunchRatio())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal dinner = BigDecimal.valueOf(dailyPrice).subtract(breakfast).subtract(lunch);

        List<String> notes = new ArrayList<>();
        boolean needsReview = false;
        if (dailyPrice == version.getMinDailyPrice().intValue() && rawAdjusted.compareTo(version.getMinDailyPrice()) < 0) {
            needsReview = true;
            notes.add("未取整日价低于最低日价，已按 " + version.getMinDailyPrice() + " 元兜底");
        }
        int minProtein = version.getMinTargetProteinG() == null ? 80 : version.getMinTargetProteinG();
        int maxProtein = version.getMaxTargetProteinG() == null ? 220 : version.getMaxTargetProteinG();
        if (proteinG < minProtein || proteinG > maxProtein) {
            needsReview = true;
            notes.add("计划蛋白 " + proteinG + "g 超出 " + minProtein + "–" + maxProtein + "g 范围，需人工复核");
        }

        return new Quote(version.getId(), version.getVersionNo(), proteinG,
                BigDecimal.valueOf(proteinG).divide(TEN, 2, RoundingMode.HALF_UP),
                baseProtein, extraProtein,
                version.getDailyBasePrice(), extraFee,
                rawReference, referencePrice, swap,
                rawAdjusted, dailyPrice,
                breakfast, lunch, dinner, needsReview, notes);
    }

    /** 替换差价 = 变动成本差 ÷ 成本折算系数，可为负。 */
    public BigDecimal swapDifference(PricingVersionDO version,
                                     BigDecimal originalVariableCost, BigDecimal replacementVariableCost) {
        if (originalVariableCost == null || replacementVariableCost == null) {
            return null;
        }
        BigDecimal divisor = version.getSwapCostDivisor();
        if (divisor == null || divisor.signum() <= 0) {
            return null;
        }
        return replacementVariableCost.subtract(originalVariableCost)
                .divide(divisor, 4, RoundingMode.HALF_UP);
    }

    private int roundToYuan(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private int clampMin(int price, PricingVersionDO version) {
        int min = version.getMinDailyPrice() == null ? 65 : version.getMinDailyPrice().intValue();
        return Math.max(min, price);
    }

    /** 展示用百分比，例如 0.25 → 25%。 */
    public BigDecimal toPercent(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP);
    }
}
