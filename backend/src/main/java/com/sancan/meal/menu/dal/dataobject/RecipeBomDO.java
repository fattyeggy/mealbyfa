package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 菜谱 BOM 行：一份菜品所需某食材的可食生重。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_recipe_bom")
public class RecipeBomDO extends BaseDO {

    private Long dishVersionId;
    private Long ingredientId;
    /** 可食生重（克），已扣除不可食部分。 */
    private BigDecimal netWeightG;
    private Integer sortNo;
    /**
     * 本行在出品中的组件类型，覆盖食材上的 componentType；为空时沿用食材的类型。
     * 同一食材可在不同组件各出现一次（如炒肉的油和拌菜的油）。
     */
    private String componentType;
    /**
     * 本行在标准一份里对应的熟重（克），做菜记录同步时写入。为空时按 版本净重 ÷ 配方生重合计 等比折算。
     * 用于把用户份量换算成实际出品重量，以及厨房按份量折算备料。
     */
    private java.math.BigDecimal cookedWeightG;
    private String note;
}
