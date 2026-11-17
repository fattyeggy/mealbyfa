package com.sancan.meal.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 周订单组：一键订整周的多张日订单。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_order_group")
public class OrderGroupDO extends BaseDO {
    private Long userId;
    private LocalDate weekStart;
    private Long couponId;
    private BigDecimal couponDiscount;
    private BigDecimal originalTotal;
    private BigDecimal payableTotal;
    private String status;
}
