package com.sancan.meal.order.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.util.CsvWriter;
import com.sancan.meal.order.controller.dto.OrderDetailResp;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.job.OrderLockJob;
import com.sancan.meal.order.service.OrderService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 后台订单：查询、退款、配送状态、手动触发锁单。 */
@RestController
@RequestMapping("/admin/meal/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderLockJob orderLockJob;

    public AdminOrderController(OrderService orderService, OrderLockJob orderLockJob) {
        this.orderService = orderService;
        this.orderLockJob = orderLockJob;
    }

    @RequirePermission(Permission.ORDER_READ)
    @GetMapping
    public CommonResult<PageResult<OrderDO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.ok(orderService.adminPage(status, serviceDate, pageNo, pageSize));
    }

    @RequirePermission(Permission.ORDER_READ)
    @GetMapping("/{id}")
    public CommonResult<OrderDetailResp> detail(@PathVariable Long id) {
        return CommonResult.ok(orderService.adminDetail(id));
    }

    /** 锁单快照（只读核对）。 */
    @RequirePermission(Permission.ORDER_READ)
    @GetMapping("/{id}/snapshot")
    public CommonResult<com.sancan.meal.order.dal.dataobject.OrderSnapshotDO> snapshot(@PathVariable Long id) {
        return CommonResult.ok(orderService.snapshot(id));
    }

    /** 配送清单导出（交给配送人员）。 */
    @RequirePermission(Permission.DELIVERY_WRITE)
    @GetMapping("/export/delivery")
    public ResponseEntity<byte[]> exportDelivery(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        List<List<Object>> rows = new ArrayList<>();
        for (OrderService.DeliveryRow row : orderService.deliveryRows(serviceDate)) {
            rows.add(List.of(row.orderNo(), row.deliveryBatch(), row.contactName(),
                    row.contactPhone(), row.address(), row.status()));
        }
        byte[] csv = CsvWriter.toCsv(
                List.of("订单号", "配送批次", "联系人", "联系电话", "地址", "状态"), rows);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("delivery-" + serviceDate + ".csv").build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @RequirePermission(Permission.ORDER_REFUND)
    @PostMapping("/{id}/refund")
    public CommonResult<Boolean> refund(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        orderService.refund(id, req == null ? null : req.reason());
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.DELIVERY_WRITE)
    @PostMapping("/{id}/delivery")
    public CommonResult<Boolean> delivery(@PathVariable Long id, @Valid @RequestBody DeliveryReq req) {
        orderService.updateDelivery(id, req.deliveryId(), req.status());
        return CommonResult.ok(true);
    }

    /** 手动触发锁单（用于补跑或联调）。 */
    @RequirePermission(Permission.ORDER_LOCK)
    @PostMapping("/lock-run")
    public CommonResult<OrderLockJob.LockRunResult> lockRun(@Valid @RequestBody LockRunReq req) {
        return CommonResult.ok(orderLockJob.run(req.serviceDate()));
    }

    public record ReasonReq(String reason) {
    }

    public record DeliveryReq(@NotNull(message = "配送记录不能为空") Long deliveryId,
                              @NotBlank(message = "状态不能为空") String status) {
    }

    public record LockRunReq(@jakarta.validation.constraints.NotNull(message = "服务日不能为空")
                             LocalDate serviceDate) {
    }
}
