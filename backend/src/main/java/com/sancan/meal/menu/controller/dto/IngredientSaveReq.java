package com.sancan.meal.menu.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 食材新增/编辑入参。 */
public record IngredientSaveReq(
        @NotBlank(message = "食材名称不能为空") @Size(max = 64, message = "食材名称过长") String name,
        @Size(max = 32) String category,
        @NotBlank(message = "采购单位不能为空") String purchaseUnit,
        @NotBlank(message = "使用单位不能为空") String usageUnit,
        @NotNull(message = "换算率不能为空")
        @DecimalMin(value = "0.0001", message = "换算率必须大于 0") BigDecimal conversionRate,
        @NotNull(message = "可食率不能为空")
        @DecimalMin(value = "0.01", message = "可食率必须大于 0")
        @DecimalMax(value = "1", message = "可食率不能大于 1") BigDecimal edibleRate,
        @DecimalMin(value = "0", message = "烹饪损耗率不能为负")
        @DecimalMax(value = "1", message = "烹饪损耗率不能大于 1") BigDecimal cookingLossRate,
        Integer shelfLifeDays,
        @Size(max = 255) String allergens,
        @NotNull(message = "移动加权价不能为空")
        @DecimalMin(value = "0", message = "价格不能为负") BigDecimal movingAvgPrice,
        String componentType,
        @DecimalMin(value = "0", message = "热量不能为负") BigDecimal kcalPer100g,
        @DecimalMin(value = "0", message = "蛋白不能为负") BigDecimal proteinPer100g,
        @DecimalMin(value = "0", message = "碳水不能为负") BigDecimal carbPer100g,
        @DecimalMin(value = "0", message = "脂肪不能为负") BigDecimal fatPer100g,
        Integer status) {
}
