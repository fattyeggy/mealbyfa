package com.sancan.meal.pricing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定价版本（计价规则 V1.0 §8）。发布后不可修改，只能新建版本。
 * 历史订单与计划都引用版本号，后台改价不会改变已确认订单的价格。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_pricing_version")
public class PricingVersionDO extends BaseDO {

    private String versionNo;
    /** DRAFT / PUBLISHED / ARCHIVED。 */
    private String status;
    private LocalDateTime effectiveAt;
    private BigDecimal dailyBasePrice;
    private Integer includedProteinG;
    @TableField("extra_protein_price_per_10g")
    private BigDecimal extraProteinPricePer10g;
    private BigDecimal minDailyPrice;
    private Integer minTargetProteinG;
    private Integer maxTargetProteinG;
    private Integer manualAdjustStepG;
    private BigDecimal swapCostDivisor;
    private BigDecimal breakfastRatio;
    private BigDecimal lunchRatio;
    private String roundingMode;
    private String createdBy;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String publishedBy;
    private LocalDateTime publishTime;
    private String remark;
}
