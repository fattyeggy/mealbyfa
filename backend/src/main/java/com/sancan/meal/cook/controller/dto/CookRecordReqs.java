package com.sancan.meal.cook.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 做菜记录入参。 */
public final class CookRecordReqs {

    private CookRecordReqs() {
    }

    public record CreateReq(
            @NotBlank(message = "请填写这锅菜的名字") @Size(max = 64, message = "名字过长") String title,
            Long dishId,
            LocalDate cookDate) {
    }

    public record UpdateReq(
            @NotBlank(message = "请填写这锅菜的名字") @Size(max = 64, message = "名字过长") String title,
            Long dishId,
            LocalDate cookDate,
            @Size(max = 255, message = "备注过长") String remark,
            @Valid CookContent content) {
    }

    /** 同步到菜品：不传 dishId 时用记录上已关联的菜品。 */
    public record SyncReq(Long dishId) {
    }
}
