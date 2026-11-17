package com.sancan.meal.kitchen.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 厨房生产汇总单 + 分装标签数据。 */
public record KitchenSummaryResp(
        LocalDate serviceDate,
        int totalPlanQty,
        int totalActualQty,
        int totalWasteQty,
        List<BatchRow> batches,
        List<LabelRow> labels) {

    public record BatchRow(
            Long id,
            String deliveryBatch,
            String batchLabel,
            Long dishVersionId,
            String dishName,
            int planQty,
            int actualQty,
            int wasteQty,
            String status) {
    }

    /** 分装标签：一张标签对应一个订单的一个餐次。 */
    public record LabelRow(
            Long orderId,
            String orderNo,
            String deliveryBatch,
            String mealType,
            String mealLabel,
            String dishName,
            Integer weightG,
            BigDecimal proteinG,
            List<String> allergens,
            String contactName) {
    }
}
