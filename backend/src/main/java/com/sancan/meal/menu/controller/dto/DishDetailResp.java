package com.sancan.meal.menu.controller.dto;

import java.math.BigDecimal;
import java.util.List;

/** 菜品详情：基本信息 + 版本列表（含 BOM 与成本拆解）。 */
public record DishDetailResp(
        Long id,
        String name,
        String category,
        String description,
        String imageUrl,
        String storageMethod,
        String reheatMethod,
        /** BOM 推导 + 额外声明合并后的过敏原。 */
        List<String> allergens,
        Integer status,
        List<Version> versions) {

    public record Version(
            Long id,
            Integer version,
            Integer netWeightG,
            Integer kcal,
            BigDecimal proteinG,
            BigDecimal carbG,
            BigDecimal fatG,
            BigDecimal cost,
            String status,
            String remark,
            List<BomLine> bom) {
    }

    public record BomLine(
            Long ingredientId,
            String ingredientName,
            String usageUnit,
            BigDecimal netWeightG,
            BigDecimal cost,
            String note) {
    }
}
