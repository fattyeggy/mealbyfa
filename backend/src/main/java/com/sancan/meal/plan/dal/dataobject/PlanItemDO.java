package com.sancan.meal.plan.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 计划餐次：个人份量（克重、营养点、价格、成本）。
 * 蛋白质替换后 {@code dishVersionId} 变化，而 {@code baseDishVersionId} 保留原始菜品用于恢复。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_plan_item")
public class PlanItemDO extends BaseDO {

    private Long planId;
    private Long userId;
    private LocalDate serviceDate;
    private String mealType;
    private Long dishVersionId;
    private Long baseDishVersionId;
    /** 该餐次的目标蛋白质，替换时保持不变。 */
    private BigDecimal targetProteinG;
    private Integer weightG;
    private BigDecimal proteinG;
    private BigDecimal carbG;
    private BigDecimal fatG;
    private Integer kcal;
    private BigDecimal points;
    private BigDecimal price;
    private BigDecimal cost;
    /** 未发生替换时的基准变动成本，用于计算替换差价。 */
    private BigDecimal baseCost;
    /** 1 表示与用户过敏原冲突，下单时需拦截。 */
    private Integer allergenConflict;
}
