package com.sancan.meal.coupon.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户持有的券。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_coupon")
public class CouponDO extends BaseDO {

    private Long templateId;
    private Long userId;
    private String code;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long usedOrderId;
    private LocalDateTime usedTime;
}
