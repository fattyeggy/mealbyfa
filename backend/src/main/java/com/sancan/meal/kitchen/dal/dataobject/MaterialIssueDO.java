package com.sancan.meal.kitchen.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 领料记录：理论用量由 BOM 推导，实际用量与损耗由厨房登记。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_material_issue")
public class MaterialIssueDO extends BaseDO {

    private Long batchId;
    private LocalDate serviceDate;
    private String deliveryBatch;
    private Long ingredientId;
    private String ingredientName;
    private BigDecimal theoreticalG;
    private BigDecimal actualG;
    private BigDecimal wasteG;
    private String reason;
}
