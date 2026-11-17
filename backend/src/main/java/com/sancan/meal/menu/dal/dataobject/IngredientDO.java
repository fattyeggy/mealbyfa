package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 食材主数据。成本口径：每份成本 = 采购单价 × 可食生重 ÷ 可食率（手册 §10）。
 * 当采购单位与使用单位不同时，先用 {@code conversionRate} 折算到使用单位。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_ingredient")
public class IngredientDO extends BaseDO {

    private String name;
    private String category;
    /** 采购单位，如 kg。 */
    private String purchaseUnit;
    /** 使用单位，如 g。 */
    private String usageUnit;
    /** 1 个采购单位 = conversionRate 个使用单位（同单位时为 1）。 */
    private BigDecimal conversionRate;
    /** 可食率，0~1，如 0.9 表示 90% 可食。 */
    private BigDecimal edibleRate;
    /** 烹饪损耗率，0~1，用于理论用量预估。 */
    private BigDecimal cookingLossRate;
    private Integer shelfLifeDays;
    /** 过敏原，逗号分隔。 */
    private String allergens;
    /** 移动加权平均价，单位：元/采购单位。 */
    private BigDecimal movingAvgPrice;
    /** PROTEIN / STAPLE / VEGETABLE / FAT / OTHER。 */
    private String componentType;
    @com.baomidou.mybatisplus.annotation.TableField("kcal_per_100g")
    private BigDecimal kcalPer100g;
    @com.baomidou.mybatisplus.annotation.TableField("protein_per_100g")
    private BigDecimal proteinPer100g;
    @com.baomidou.mybatisplus.annotation.TableField("carb_per_100g")
    private BigDecimal carbPer100g;
    @com.baomidou.mybatisplus.annotation.TableField("fat_per_100g")
    private BigDecimal fatPer100g;
    /** 0 启用，1 停用。 */
    private Integer status;
}
