package com.sancan.meal.menu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 菜品版本与 BOM 入参。版本号由服务端递增分配。 */
public record DishVersionSaveReq(
        @NotNull(message = "成品净重不能为空") Integer netWeightG,
        Integer kcal,
        BigDecimal proteinG,
        BigDecimal carbG,
        BigDecimal fatG,
        @Size(max = 255) String remark,
        @NotEmpty(message = "菜谱 BOM 不能为空") List<BomLineReq> bom) {

    public record BomLineReq(
            @NotNull(message = "食材不能为空") Long ingredientId,
            @NotNull(message = "可食生重不能为空") BigDecimal netWeightG,
            @Size(max = 255) String note,
            /** 可选：PROTEIN / SAUCE / STAPLE / VEGETABLE / FAT / OTHER，覆盖食材上的组件类型。 */
            String componentType,
            /** 可选：本行在标准一份里的熟重（克）。 */
            BigDecimal cookedWeightG) {
    }

    /** 兼容仅传菜品名与分类的简化建档场景。 */
    public record DishSaveReq(
            @NotBlank(message = "菜品名称不能为空") @Size(max = 64) String name,
            @NotBlank(message = "菜品分类不能为空") @Size(max = 32) String category,
            @Size(max = 500) String description,
            @Size(max = 255) String imageUrl,
            @Size(max = 255) String storageMethod,
            @Size(max = 255) String reheatMethod,
            @Size(max = 255) String extraAllergens,
            Integer status) {
    }
}
