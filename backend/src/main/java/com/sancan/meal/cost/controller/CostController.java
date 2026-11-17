package com.sancan.meal.cost.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.cost.dal.dataobject.CostRuleDO;
import com.sancan.meal.cost.service.CostRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 成本参数与成本报表（手册 §10）。 */
@RestController
@RequestMapping("/admin/meal/cost")
public class CostController {

    private final CostRuleService costRuleService;

    public CostController(CostRuleService costRuleService) {
        this.costRuleService = costRuleService;
    }

    @RequirePermission(Permission.COST_READ)
    @GetMapping("/rules")
    public CommonResult<List<CostRuleDO>> rules() {
        return CommonResult.ok(costRuleService.listRules());
    }

    @RequirePermission(Permission.COST_WRITE)
    @PutMapping("/rules/{key}")
    public CommonResult<Boolean> update(@PathVariable String key, @Valid @RequestBody UpdateReq req) {
        costRuleService.updateRule(key, req.value());
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.COST_READ)
    @GetMapping("/report")
    public CommonResult<CostReportResp> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return CommonResult.ok(costRuleService.report(start, end));
    }

    public record UpdateReq(@NotNull(message = "参数值不能为空") BigDecimal value) {
    }
}
