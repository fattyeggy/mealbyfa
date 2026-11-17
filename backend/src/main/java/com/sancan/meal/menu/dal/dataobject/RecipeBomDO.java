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
    private String note;
}
