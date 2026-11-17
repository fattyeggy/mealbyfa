package com.sancan.meal.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 订单明细：下单时的价格、克重与营养固定下来。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_order_item")
public class OrderItemDO extends BaseDO {

    private Long orderId;
    private String mealType;
    private Long dishVersionId;
    private String dishName;
    private String deliveryBatch;
    private Integer weightG;
    private BigDecimal proteinG;
    private Integer kcal;
    private BigDecimal points;
    private BigDecimal price;
    private BigDecimal cost;
}
