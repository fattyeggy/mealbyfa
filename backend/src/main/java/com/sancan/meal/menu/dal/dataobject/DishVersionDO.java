package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 菜品版本：营养与成本按版本冻结，历史订单与历史菜单只引用版本，不随主数据变化。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_dish_version")
public class DishVersionDO extends BaseDO {

    private Long dishId;
    private Integer version;
    /** 成品净重（克）。 */
    private Integer netWeightG;
    private Integer kcal;
    private BigDecimal proteinG;
    private BigDecimal carbG;
    private BigDecimal fatG;
    /** 由 BOM 计算的单份食材成本（元）。 */
    private BigDecimal cost;
    private String status;
    private String remark;
}
