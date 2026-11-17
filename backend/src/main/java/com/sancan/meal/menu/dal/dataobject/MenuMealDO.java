package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 餐次配置：一天早/午/晚各一条，指向菜品版本与配送批次。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_menu_meal")
public class MenuMealDO extends BaseDO {

    private Long menuId;
    private Long menuDayId;
    private String mealType;
    private Long dishVersionId;
    private String deliveryBatch;
    private Integer sortNo;
}
