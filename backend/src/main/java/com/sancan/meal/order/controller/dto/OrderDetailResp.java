package com.sancan.meal.order.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 订单详情。 */
public record OrderDetailResp(
        Long id,
        String orderNo,
        LocalDate serviceDate,
        String status,
        String statusLabel,
        BigDecimal originalAmount,
        String pricingVersionNo,
        BigDecimal rawPrice,
        BigDecimal swapDifference,
        Long couponId,
        BigDecimal couponAmount,
        BigDecimal balanceAmount,
        BigDecimal creditAmount,
        BigDecimal cashAmount,
        BigDecimal payAmount,
        String contactName,
        String contactPhone,
        String address,
        LocalDateTime createTime,
        LocalDateTime lockTime,
        String lockFailReason,
        LocalDateTime cancelTime,
        String cancelReason,
        boolean canCancel,
        boolean hasSnapshot,
        boolean hasDelivery,
        List<Item> items,
        List<Delivery> deliveries) {

    public record Item(
            String mealType,
            String mealLabel,
            Long dishVersionId,
            String dishName,
            String deliveryBatch,
            Integer weightG,
            BigDecimal proteinG,
            Integer kcal,
            BigDecimal points,
            BigDecimal price) {
    }

    public record Delivery(Long id, String deliveryBatch, String status, LocalDateTime deliverTime,
                           java.time.LocalDate serviceDate, java.time.LocalDate actualDeliveryDate,
                           String deliverySlot) {
    }
}
