package com.sancan.meal.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.coupon.service.CouponService;
import com.sancan.meal.order.controller.dto.CreateOrderReq;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.dataobject.OrderGroupDO;
import com.sancan.meal.order.dal.mapper.OrderGroupMapper;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.enums.OrderStatus;
import com.sancan.meal.plan.service.PlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一键订整周：生成一组日订单，优惠按日金额比例分摊。
 *
 * <p>规则（评审 D1-A）：默认本周所有未锁单且未截止的服务日，可取消其中几天；
 * 周券按合计金额判断门槛，每周限用一张；优惠按比例分摊，尾差计入最后一个服务日；
 * 锁单仍按服务日独立执行。
 */
@Service
public class WeeklyOrderService {

    private final OrderMapper orderMapper;
    private final OrderGroupMapper groupMapper;
    private final PlanService planService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final OrderDeadline orderDeadline;

    public WeeklyOrderService(OrderMapper orderMapper, OrderGroupMapper groupMapper,
                              PlanService planService, OrderService orderService,
                              CouponService couponService, OrderDeadline orderDeadline) {
        this.orderMapper = orderMapper;
        this.groupMapper = groupMapper;
        this.planService = planService;
        this.orderService = orderService;
        this.couponService = couponService;
        this.orderDeadline = orderDeadline;
    }

    public record DayQuote(LocalDate serviceDate, BigDecimal originalAmount,
                           BigDecimal discount, BigDecimal payable, boolean bookable) {
    }

    public record QuoteResp(LocalDate weekStart, List<DayQuote> days, BigDecimal originalTotal,
                            BigDecimal couponDiscount, BigDecimal payableTotal,
                            boolean couponValid, String couponMessage) {
    }

    public record CreateReq(LocalDate weekStart, List<LocalDate> dates, Long couponId,
                            String contactName, String contactPhone, String address, String remark) {
    }

    public record CreateResp(Long groupId, int orderCount, BigDecimal originalTotal,
                             BigDecimal couponDiscount, BigDecimal payableTotal, List<String> orderNos) {
    }

    /** 候选取餐日：计划内有、未截止、未下过有效订单。 */
    private List<LocalDate> candidates(Long userId, LocalDate weekStart) {
        List<LocalDate> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            if (!date.isAfter(orderDeadline.now().toLocalDate()) || orderDeadline.isClosed(date)) {
                continue;
            }
            try {
                planService.itemsOfDay(userId, date);
            } catch (ServiceException ex) {
                continue;
            }
            long existing = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>()
                    .eq(OrderDO::getUserId, userId)
                    .eq(OrderDO::getServiceDate, date)
                    .eq(OrderDO::getActiveKey, 1));
            if (existing == 0) {
                result.add(date);
            }
        }
        return result;
    }

    private List<LocalDate> resolveDates(Long userId, LocalDate weekStart, List<LocalDate> requested) {
        List<LocalDate> candidates = candidates(userId, weekStart);
        if (requested == null || requested.isEmpty()) {
            return candidates;
        }
        Set<LocalDate> allowed = new LinkedHashSet<>(candidates);
        List<LocalDate> chosen = requested.stream().filter(allowed::contains).distinct().sorted().toList();
        if (chosen.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "所选日期都不可预订");
        }
        if (chosen.size() != requested.stream().distinct().count()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "部分日期已截止或已有订单，请刷新后重选");
        }
        return chosen;
    }

    public QuoteResp quote(Long userId, LocalDate weekStart, List<LocalDate> dates, Long couponId) {
        List<LocalDate> chosen = resolveDates(userId, weekStart, dates);
        List<BigDecimal> prices = chosen.stream()
                .map(d -> BigDecimal.valueOf(planService.dailyQuote(userId, d).dailyPrice()))
                .toList();
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = BigDecimal.ZERO;
        boolean valid = true;
        String message = couponId == null ? null : "已选券";
        if (couponId != null) {
            try {
                discount = couponService.calculateDiscount(userId, couponId, total);
            } catch (ServiceException ex) {
                valid = false;
                message = ex.getMessage();
            }
        }
        List<BigDecimal> allocated = allocate(prices, total, discount);
        List<DayQuote> days = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            days.add(new DayQuote(chosen.get(i), prices.get(i), allocated.get(i),
                    prices.get(i).subtract(allocated.get(i)).max(BigDecimal.ZERO), true));
        }
        BigDecimal payable = total.subtract(discount).max(BigDecimal.ZERO);
        return new QuoteResp(weekStart, days, total, discount, payable, valid, message);
    }

    /** 按金额比例分摊，尾差计入最后一天。 */
    private List<BigDecimal> allocate(List<BigDecimal> prices, BigDecimal total, BigDecimal discount) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal used = BigDecimal.ZERO;
        for (int i = 0; i < prices.size(); i++) {
            BigDecimal amount;
            if (i == prices.size() - 1) {
                amount = discount.subtract(used);
            } else if (total.signum() == 0) {
                amount = BigDecimal.ZERO;
            } else {
                amount = discount.multiply(prices.get(i))
                        .divide(total, 2, RoundingMode.HALF_UP);
                used = used.add(amount);
            }
            result.add(amount.max(BigDecimal.ZERO).min(prices.get(i)));
        }
        return result;
    }

    @Transactional
    public CreateResp create(Long userId, CreateReq req) {
        LocalDate weekStart = req.weekStart() == null ? LocalDate.now() : req.weekStart();
        List<LocalDate> dates = resolveDates(userId, weekStart, req.dates());
        if (dates.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "本周没有可预订的日期");
        }
        QuoteResp quote = quote(userId, weekStart, dates, req.couponId());
        if (req.couponId() != null && !quote.couponValid()) {
            throw new ServiceException(ErrorCode.COUPON_UNAVAILABLE, quote.couponMessage());
        }

        OrderGroupDO group = new OrderGroupDO();
        group.setUserId(userId);
        group.setWeekStart(weekStart);
        group.setCouponId(req.couponId());
        group.setCouponDiscount(quote.couponDiscount());
        group.setOriginalTotal(quote.originalTotal());
        group.setPayableTotal(quote.payableTotal());
        group.setStatus("ACTIVE");
        groupMapper.insert(group);
        if (req.couponId() != null) {
            // 券预占在「周订单组」上，一组只占一次
            couponService.reserve(userId, req.couponId(), group.getId());
        }

        List<String> orderNos = new ArrayList<>();
        for (DayQuote day : quote.days()) {
            CreateOrderReq create = new CreateOrderReq(day.serviceDate(), null,
                    req.contactName(), req.contactPhone(), req.address(), req.remark());
            var detail = orderService.create(userId, create);
            OrderDO order = orderMapper.selectById(detail.id());
            order.setGroupId(group.getId());
            order.setCouponAmount(day.discount());
            orderMapper.updateById(order);
            orderNos.add(order.getOrderNo());
        }
        return new CreateResp(group.getId(), orderNos.size(), quote.originalTotal(),
                quote.couponDiscount(), quote.payableTotal(), orderNos);
    }

    public List<Map<String, Object>> list(Long userId, LocalDate weekStart) {
        List<OrderGroupDO> groups = groupMapper.selectList(new LambdaQueryWrapper<OrderGroupDO>()
                .eq(OrderGroupDO::getUserId, userId)
                .eq(OrderGroupDO::getWeekStart, weekStart)
                .orderByDesc(OrderGroupDO::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderGroupDO group : groups) {
            List<OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                    .eq(OrderDO::getGroupId, group.getId())
                    .orderByAsc(OrderDO::getServiceDate));
            result.add(Map.of(
                    "groupId", group.getId(),
                    "status", group.getStatus(),
                    "couponDiscount", group.getCouponDiscount(),
                    "originalTotal", group.getOriginalTotal(),
                    "payableTotal", group.getPayableTotal(),
                    "orders", orders.stream().map(o -> Map.of(
                            "id", o.getId(), "serviceDate", o.getServiceDate().toString(),
                            "status", o.getStatus(), "originalAmount", o.getOriginalAmount(),
                            "couponAmount", o.getCouponAmount() == null ? BigDecimal.ZERO : o.getCouponAmount())).toList()));
        }
        return result;
    }

    @Transactional
    public void cancel(Long userId, Long groupId) {
        OrderGroupDO group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "周订单不存在");
        }
        List<OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getGroupId, groupId));
        for (OrderDO order : orders) {
            if (OrderStatus.valueOf(order.getStatus()).isLocked()) {
                throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "已有订单锁定，不能整周取消");
            }
        }
        for (OrderDO order : orders) {
            if (order.getActiveKey() != null && order.getActiveKey() == 1) {
                orderService.cancel(userId, order.getId(), "整周取消");
            }
        }
        if (group.getCouponId() != null) {
            couponService.releaseForOrder(group.getCouponId(), groupId);
        }
        group.setStatus("CANCELLED");
        groupMapper.updateById(group);
    }
}
