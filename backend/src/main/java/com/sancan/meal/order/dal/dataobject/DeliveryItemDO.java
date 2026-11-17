package com.sancan.meal.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 配送明细：某次配送实际承运的餐次（跨日早餐在此归属）。 */
@Data
@TableName("meal_delivery_item")
public class DeliveryItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deliveryId;
    private Long orderId;
    private Long orderItemId;
    private String mealType;
    private LocalDate serviceDate;
    private LocalDateTime createTime;
}
