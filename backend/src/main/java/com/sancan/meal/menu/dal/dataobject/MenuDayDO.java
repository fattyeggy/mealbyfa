package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 服务日汇总：一天三食的合计营养与重量。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_menu_day")
public class MenuDayDO extends BaseDO {

    private Long menuId;
    private LocalDate serviceDate;
    /** 1=周一 … 7=周日。 */
    private Integer dayOfWeek;
    private Integer totalWeightG;
    private BigDecimal totalProteinG;
    private Integer totalKcal;
    private BigDecimal totalPoints;
}
