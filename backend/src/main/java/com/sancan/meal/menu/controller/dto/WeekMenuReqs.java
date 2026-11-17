package com.sancan.meal.menu.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 周菜单相关入参。 */
public final class WeekMenuReqs {

    private WeekMenuReqs() {
    }

    /** 新建周菜单：startDate 必须是周一，结束日自动为周日。 */
    public record Create(
            @NotBlank(message = "主题不能为空") @Size(max = 64) String themeName,
            @NotBlank(message = "地区不能为空") @Size(max = 64) String region,
            @NotNull(message = "开始日期不能为空") LocalDate startDate,
            @NotBlank(message = "版本号不能为空") @Size(max = 32) String version,
            @Size(max = 255) String remark) {
    }

    /** 编辑菜单头信息，仅草稿可改。 */
    public record UpdateMeta(
            @NotBlank(message = "主题不能为空") @Size(max = 64) String themeName,
            @Size(max = 255) String remark,
            BigDecimal expectedCostRate) {
    }

    /** 批量写入 21 个餐次。未提交的餐次视为删除。 */
    public record UpsertMeals(@NotNull(message = "餐次列表不能为空") List<Slot> meals) {
    }

    public record Slot(
            @NotNull(message = "服务日不能为空") LocalDate serviceDate,
            @NotBlank(message = "餐次类型不能为空") String mealType,
            @NotNull(message = "菜品版本不能为空") Long dishVersionId) {
    }

    /** 复制上一周菜单到新的开始日。 */
    public record Copy(
            @NotNull(message = "新开始日期不能为空") LocalDate startDate,
            @NotBlank(message = "版本号不能为空") @Size(max = 32) String version) {
    }
}
