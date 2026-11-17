package com.sancan.meal.plan.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 个人营养计划：一周一版，记录目标蛋白质与营养点。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_nutrition_plan")
public class NutritionPlanDO extends BaseDO {

    private Long userId;
    private LocalDate weekStart;
    private BigDecimal targetProteinG;
    /** 建档公式算出的原始目标，未夹取。 */
    private BigDecimal rawTargetProteinG;
    private Integer targetKcal;
    private Integer targetFatG;
    private Integer targetCarbG;
    private Integer proteinTier;
    private String configVersion;
    private String inputSnapshot;
    private BigDecimal dailyPoints;
    private String status;
    private Integer version;
    /** 生成计划时绑定的定价版本，改价不影响已确认计划。 */
    private Long pricingVersionId;
    private String pricingVersionNo;
    /** 生成计划时绑定的菜单，锁单快照按此读取而不是最新发布菜单。 */
    private Long sourceMenuId;
    private String sourceMenuVersion;
    /** 1 表示存在待人工复核事项（份量越界、价格兜底等）。 */
    private Integer needsReview;
    private String reviewNote;
}
