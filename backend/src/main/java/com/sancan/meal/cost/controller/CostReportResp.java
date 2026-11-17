package com.sancan.meal.cost.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 成本报表（手册 §10「成本报表」）：按日列出三层成本与毛利。 */
public record CostReportResp(
        LocalDate start,
        LocalDate end,
        List<DayRow> days,
        DayRow totals) {

    public record DayRow(
            LocalDate serviceDate,
            int orders,
            int meals,
            /** 实收：锁单时从账户实际扣除的金额。 */
            BigDecimal revenue,
            /** 第一层：食材成本。 */
            BigDecimal foodCost,
            /** 第二层：包装分摊。 */
            BigDecimal packageCost,
            /** 第三层：配送分摊。 */
            BigDecimal deliveryCost,
            BigDecimal paymentFee,
            BigDecimal fixedCost,
            BigDecimal totalCost,
            BigDecimal grossProfit,
            BigDecimal foodCostRate,
            BigDecimal totalCostRate,
            /** 已登记领料食材的 BOM 理论成本（与 materialActualCost 同口径）。 */
            BigDecimal materialTheoreticalCost,
            /** 已登记领料的实际成本；无登记为 0。 */
            BigDecimal materialActualCost,
            /** 实际 − 理论；未登记领料时为 null。 */
            BigDecimal materialDiff,
            boolean hasMaterialIssue) {
    }
}
