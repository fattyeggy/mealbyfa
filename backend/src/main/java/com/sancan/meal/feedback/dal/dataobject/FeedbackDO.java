package com.sancan.meal.feedback.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 完餐反馈：一单一餐最多一条。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_feedback")
public class FeedbackDO extends BaseDO {

    private Long orderId;
    private Long userId;
    private LocalDate serviceDate;
    private String mealType;
    /** EATEN / QUARTER / HALF / NONE。 */
    private String leftoverRatio;
    /** 饱腹感 1–5。 */
    private Integer satiety;
    private String trainingState;
    private String content;
}
