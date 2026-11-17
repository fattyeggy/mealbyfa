package com.sancan.meal.coupon.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 券模板。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_coupon_template")
public class CouponTemplateDO extends BaseDO {

    private String name;
    private String type;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;
    private Integer validDays;
    private Integer status;
}
