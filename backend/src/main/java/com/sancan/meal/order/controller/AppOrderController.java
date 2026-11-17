package com.sancan.meal.order.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.order.controller.dto.CreateOrderReq;
import com.sancan.meal.order.controller.dto.OrderDetailResp;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.service.OrderService;
import com.sancan.meal.order.service.WeeklyOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 小程序订单（手册 §5「个人计划到订单」）。 */
@RestController
@RequestMapping("/app/orders")
public class AppOrderController {

    private final OrderService orderService;
    private final WeeklyOrderService weeklyOrderService;

    public AppOrderController(OrderService orderService, WeeklyOrderService weeklyOrderService) {
        this.orderService = orderService;
        this.weeklyOrderService = weeklyOrderService;
    }

    @PostMapping
    public CommonResult<OrderDetailResp> create(@Valid @RequestBody CreateOrderReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(orderService.create(userId, req));
    }

    /** 下单试算（含券）。 */
    @PostMapping("/quote")
    public CommonResult<OrderService.QuoteResp> quote(@RequestBody QuoteReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(orderService.quote(userId, req.serviceDate(), req.couponId()));
    }

    public record QuoteReq(java.time.LocalDate serviceDate, Long couponId) {
    }

    /** 一键订整周：试算（不落库）。 */
    @PostMapping("/weekly/quote")
    public CommonResult<WeeklyOrderService.QuoteResp> weeklyQuote(@RequestBody WeeklyQuoteReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(weeklyOrderService.quote(userId, req.weekStart(), req.dates(), req.couponId()));
    }

    /** 一键订整周：生成一组日订单。 */
    @PostMapping("/weekly")
    public CommonResult<WeeklyOrderService.CreateResp> weeklyCreate(@RequestBody WeeklyOrderService.CreateReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(weeklyOrderService.create(userId, req));
    }

    @GetMapping("/weekly")
    public CommonResult<List<Map<String, Object>>> weeklyList(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(weeklyOrderService.list(userId, weekStart));
    }

    @PostMapping("/weekly/{groupId}/cancel")
    public CommonResult<Boolean> weeklyCancel(@PathVariable Long groupId) {
        Long userId = UserContext.requireMemberId();
        weeklyOrderService.cancel(userId, groupId);
        return CommonResult.ok(true);
    }

    public record WeeklyQuoteReq(java.time.LocalDate weekStart, java.util.List<java.time.LocalDate> dates,
                                 Long couponId) {
    }

    @GetMapping
    public CommonResult<PageResult<OrderDO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(orderService.page(userId, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public CommonResult<OrderDetailResp> detail(@PathVariable Long id) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(orderService.detail(userId, id));
    }

    @PostMapping("/{id}/cancel")
    public CommonResult<Boolean> cancel(@PathVariable Long id,
                                        @RequestBody(required = false) CancelReq req) {
        Long userId = UserContext.requireMemberId();
        orderService.cancel(userId, id, req == null ? null : req.reason());
        return CommonResult.ok(true);
    }

    public record CancelReq(String reason) {
    }
}
