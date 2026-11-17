package com.sancan.meal.kitchen.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 临时替换记录。锁单快照不随之改变——快照是契约，替换是履约偏差，
 * 因此必须保留审批人与用户通知状态（手册 §9）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_substitution")
public class SubstitutionDO extends BaseDO {

    private Long orderId;
    private LocalDate serviceDate;
    private String mealType;
    private Long fromDishVersionId;
    private Long toDishVersionId;
    private String reason;
    private Long approvalBy;
    private LocalDateTime approvalTime;
    /** PENDING / SENT。 */
    private String notifyStatus;
    private LocalDateTime notifyTime;
}
