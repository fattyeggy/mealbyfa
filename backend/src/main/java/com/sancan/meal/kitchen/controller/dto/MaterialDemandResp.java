package com.sancan.meal.kitchen.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 食材需求汇总：由 BOM × 生产份数推导，供采购使用。 */
public record MaterialDemandResp(
        LocalDate serviceDate,
        BigDecimal totalCost,
        List<Item> items) {

    public record Item(
            Long ingredientId,
            String ingredientName,
            String usageUnit,
            /** 可食生重合计（克）。 */
            BigDecimal edibleG,
            /** 采购重量（克）：可食生重 ÷ 可食率。 */
            BigDecimal purchaseG,
            /** 采购单位。 */
            String purchaseUnit,
            /** 按采购重量折算的金额。 */
            BigDecimal cost,
            /** 已登记的实际领料（无登记为 null）。 */
            BigDecimal actualG,
            BigDecimal wasteG,
            /** 实际领料折算成本；未登记领料时为 null。 */
            BigDecimal actualCost) {
    }
}
