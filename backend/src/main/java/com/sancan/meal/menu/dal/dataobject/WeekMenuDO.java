package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 周菜单头：一个自然周的地区主题（手册 §6）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_week_menu")
public class WeekMenuDO extends BaseDO {

    private String themeName;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 版本号，与 startDate 共同唯一；发布后不可原地覆盖。 */
    private String version;
    private String status;
    private LocalDateTime publishTime;
    /** 全周单份标准食材成本合计（元）。 */
    private BigDecimal standardCost;
    /** 预计食材成本率，如 0.3820 表示 38.20%。 */
    private BigDecimal expectedCostRate;
    private String remark;
}
