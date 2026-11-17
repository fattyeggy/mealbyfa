package com.sancan.meal.pricing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 已审核的替换选项，便于追溯成本口径（V1.0 §6.4）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_pricing_version_swap")
public class PricingVersionSwapDO extends BaseDO {

    private Long pricingVersionId;
    private String mealType;
    private Long fromDishVersionId;
    private Long toDishVersionId;
    /** 审核时的标准份成本差，仅作审计参考，不参与计算。 */
    private BigDecimal costDelta;
    /** 审核时冻结的原餐次标准份变动成本。 */
    private BigDecimal fromVersionCost;
    /** 审核时冻结的替换餐次标准份变动成本。 */
    private BigDecimal toVersionCost;
    private LocalDateTime costFrozenTime;
    /** APPROVED / DISABLED。 */
    private String status;
    private String remark;
}
