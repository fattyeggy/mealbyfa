package com.sancan.meal.plan.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 复核事项。计划的 {@code needs_review} 只是汇总冗余字段，
 * 复核事实以本表为准（P4.6 §2.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_plan_review")
public class PlanReviewDO extends BaseDO {

    private Long planId;
    private Long userId;
    private LocalDate serviceDate;
    private String mealType;
    /** PROTEIN_RANGE / SCALE_CLAMP / PRICE_FLOOR / COST_MISSING / MANUAL。 */
    private String reviewType;
    private String reviewMessage;
    /** PENDING / RESOLVED。 */
    private String status;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
    private String resolutionNote;
}
