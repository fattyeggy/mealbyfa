package com.sancan.meal.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 配送记录：每日两个批次（午餐 / 晚餐及次日早餐）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_delivery")
public class DeliveryDO extends BaseDO {

    private Long orderId;
    private String deliveryBatch;
    /** 餐次所属服务日。 */
    private LocalDate serviceDate;
    /** 实际配送日期；D 日早餐为 D-1。 */
    private LocalDate actualDeliveryDate;
    /** LUNCH / DINNER / BREAKFAST。 */
    private String deliverySlot;
    private String status;
    private String contactName;
    private String contactPhone;
    private String address;
    private LocalDateTime deliverTime;
    private String remark;
}
