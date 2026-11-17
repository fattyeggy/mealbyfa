package com.sancan.meal.cost.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 成本参数：包装分摊、配送分摊、支付手续费与固定成本（手册 §10 三层成本）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_cost_rule")
public class CostRuleDO extends BaseDO {

    private String ruleKey;
    private BigDecimal ruleValue;
    private String description;
}
