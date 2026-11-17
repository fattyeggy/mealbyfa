package com.sancan.meal.kitchen.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 生产批次：同一服务日、同一配送批次、同一菜品合并成一条。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_production_batch")
public class ProductionBatchDO extends BaseDO {

    private LocalDate serviceDate;
    private String deliveryBatch;
    private Long dishVersionId;
    private String dishName;
    private Integer planQty;
    private Integer actualQty;
    private Integer wasteQty;
    /** PLANNED / PREPARING / DONE。 */
    private String status;
    private String remark;
}
