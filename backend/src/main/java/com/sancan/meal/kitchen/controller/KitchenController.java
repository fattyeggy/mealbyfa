package com.sancan.meal.kitchen.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.util.CsvWriter;
import com.sancan.meal.kitchen.controller.dto.KitchenSummaryResp;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.kitchen.controller.dto.KitchenSummaryResp;
import com.sancan.meal.kitchen.controller.dto.MaterialDemandResp;
import com.sancan.meal.kitchen.dal.dataobject.SubstitutionDO;
import com.sancan.meal.kitchen.service.KitchenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 厨房生产与分装（手册 §9）。 */
@RestController
@RequestMapping("/admin/meal")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    /** 由已锁订单汇总生成生产批次（可重复执行）。 */
    @RequirePermission(Permission.KITCHEN_WRITE)
    @PostMapping("/kitchen/generate")
    public CommonResult<KitchenSummaryResp> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return CommonResult.ok(kitchenService.generate(serviceDate));
    }

    @RequirePermission(Permission.KITCHEN_READ)
    @GetMapping("/kitchen/summary")
    public CommonResult<KitchenSummaryResp> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return CommonResult.ok(kitchenService.summary(serviceDate));
    }

    /** 食材需求（采购建议）。 */
    @RequirePermission(Permission.KITCHEN_READ)
    @GetMapping("/kitchen/material-demand")
    public CommonResult<MaterialDemandResp> materialDemand(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return CommonResult.ok(kitchenService.materialDemand(serviceDate));
    }

    /** 生产汇总单导出（CSV）。 */
    @RequirePermission(Permission.KITCHEN_READ)
    @GetMapping("/kitchen/export/production")
    public ResponseEntity<byte[]> exportProduction(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        KitchenSummaryResp summary = kitchenService.summary(serviceDate);
        List<List<Object>> rows = new ArrayList<>();
        for (KitchenSummaryResp.BatchRow batch : summary.batches()) {
            rows.add(List.of(serviceDate.toString(), batch.batchLabel(), batch.dishName(),
                    batch.planQty(), batch.actualQty(), batch.wasteQty(), batch.status()));
        }
        byte[] csv = CsvWriter.toCsv(
                List.of("服务日", "配送批次", "菜品", "计划份数", "实做份数", "报废份数", "状态"), rows);
        return csvResponse(csv, "production-" + serviceDate + ".csv");
    }

    /** 分装标签导出（每个订单每餐一行）。 */
    @RequirePermission(Permission.KITCHEN_READ)
    @GetMapping("/kitchen/export/labels")
    public ResponseEntity<byte[]> exportLabels(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        KitchenSummaryResp summary = kitchenService.summary(serviceDate);
        List<List<Object>> rows = new ArrayList<>();
        for (KitchenSummaryResp.LabelRow label : summary.labels()) {
            rows.add(List.of(label.orderNo(), label.deliveryBatch(), label.mealLabel(),
                    label.dishName(), label.weightG(), label.proteinG(),
                    String.join("、", label.allergens()), label.contactName()));
        }
        byte[] csv = CsvWriter.toCsv(
                List.of("订单号", "配送批次", "餐次", "菜品", "克重(g)", "蛋白(g)", "过敏原", "联系人"), rows);
        return csvResponse(csv, "labels-" + serviceDate + ".csv");
    }

    private ResponseEntity<byte[]> csvResponse(byte[] csv, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @RequirePermission(Permission.KITCHEN_WRITE)
    @PutMapping("/kitchen/batches/{id}/status")
    public CommonResult<Boolean> status(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        kitchenService.updateBatchStatus(id, req.status());
        return CommonResult.ok(true);
    }

    /** 登记实际领料与损耗。 */
    @RequirePermission(Permission.KITCHEN_WRITE)
    @PostMapping("/kitchen/batches/{id}/issue")
    public CommonResult<Boolean> issue(@PathVariable Long id, @Valid @RequestBody IssueReq req) {
        kitchenService.recordIssue(id, req.ingredientId(), req.actualG(), req.wasteG(), req.reason());
        return CommonResult.ok(true);
    }

    /** 登记成品产量与报废。 */
    @RequirePermission(Permission.KITCHEN_WRITE)
    @PostMapping("/kitchen/batches/{id}/output")
    public CommonResult<Boolean> output(@PathVariable Long id, @Valid @RequestBody OutputReq req) {
        kitchenService.recordOutput(id, req.actualQty(), req.wasteQty(), req.remark());
        return CommonResult.ok(true);
    }

    /** 登记临时替换（锁单后的履约偏差，保留审批与通知）。 */
    @RequirePermission(Permission.KITCHEN_WRITE)
    @PostMapping("/orders/{orderId}/substitutions")
    public CommonResult<SubstitutionDO> substitution(@PathVariable Long orderId,
                                                     @Valid @RequestBody SubstitutionReq req) {
        Long adminId = UserContext.requireAdminId();
        return CommonResult.ok(kitchenService.substitution(
                orderId, req.mealType(), req.toDishVersionId(), req.reason(), adminId));
    }

    @RequirePermission(Permission.KITCHEN_READ)
    @GetMapping("/orders/{orderId}/substitutions")
    public CommonResult<List<SubstitutionDO>> substitutions(@PathVariable Long orderId) {
        return CommonResult.ok(kitchenService.substitutions(orderId));
    }

    @RequirePermission(Permission.KITCHEN_WRITE)
    @PostMapping("/substitutions/{id}/notify")
    public CommonResult<Boolean> notify(@PathVariable Long id) {
        kitchenService.markNotified(id);
        return CommonResult.ok(true);
    }

    public record StatusReq(@NotBlank(message = "状态不能为空") String status) {
    }

    public record IssueReq(@NotNull(message = "食材不能为空") Long ingredientId,
                           @NotNull(message = "实际领料不能为空") BigDecimal actualG,
                           BigDecimal wasteG,
                           String reason) {
    }

    public record OutputReq(@NotNull(message = "成品数量不能为空") Integer actualQty,
                            @NotNull(message = "报废数量不能为空") Integer wasteQty,
                            String remark) {
    }

    public record SubstitutionReq(@NotBlank(message = "餐次不能为空") String mealType,
                                  @NotNull(message = "替换目标不能为空") Long toDishVersionId,
                                  String reason) {
    }
}
