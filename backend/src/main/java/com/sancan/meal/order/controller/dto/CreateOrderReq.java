package com.sancan.meal.order.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 确认方案并下单。地址为下单时的快照，后续地址簿改动不影响历史订单。 */
public record CreateOrderReq(
        @NotNull(message = "服务日不能为空") LocalDate serviceDate,
        Long couponId,
        @NotBlank(message = "联系人不能为空") @Size(max = 64) String contactName,
        @NotBlank(message = "联系电话不能为空") @Size(max = 32) String contactPhone,
        @NotBlank(message = "配送地址不能为空") @Size(max = 255) String address,
        @Size(max = 255) String remark) {
}
