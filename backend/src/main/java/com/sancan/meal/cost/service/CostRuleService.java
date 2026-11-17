package com.sancan.meal.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.kitchen.controller.dto.MaterialDemandResp;
import com.sancan.meal.kitchen.service.KitchenService;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.cost.controller.CostReportResp;
import com.sancan.meal.cost.dal.dataobject.CostRuleDO;
import com.sancan.meal.cost.dal.mapper.CostRuleMapper;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.dataobject.OrderItemDO;
import com.sancan.meal.order.dal.mapper.OrderItemMapper;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.enums.OrderStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 餐饮成本（手册 §10「三层成本」）。
 *
 * <pre>
 * 食材成本 = Σ 单份成本（来自菜品 BOM，锁单时已固定）
 * 包装分摊 = 餐次数 × 每餐包装
 * 配送分摊 = 订单数 × 每单配送
 * 支付手续费 = 实收 × 费率
 * 固定成本 = 订单数 × 每单固定分摊
 * </pre>
 *
 * 参数全部可在后台调整；数值为初始假设，已列入待业务确认清单。
 */
@Service
public class CostRuleService {

    public static final String KEY_PACKAGING_PER_MEAL = "packaging_per_meal";
    public static final String KEY_DELIVERY_PER_ORDER = "delivery_per_order";
    public static final String KEY_PAYMENT_FEE_RATE = "payment_fee_rate";
    public static final String KEY_FIXED_COST_PER_ORDER = "fixed_cost_per_order";
    public static final String KEY_TARGET_COST_RATE = "target_cost_rate";

    private static final Map<String, BigDecimal> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(KEY_PACKAGING_PER_MEAL, new BigDecimal("2.0000"));
        DEFAULTS.put(KEY_DELIVERY_PER_ORDER, new BigDecimal("12.0000"));
        DEFAULTS.put(KEY_PAYMENT_FEE_RATE, new BigDecimal("0.0060"));
        DEFAULTS.put(KEY_FIXED_COST_PER_ORDER, new BigDecimal("3.0000"));
        DEFAULTS.put(KEY_TARGET_COST_RATE, new BigDecimal("0.4000"));
    }

    private final CostRuleMapper costRuleMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final KitchenService kitchenService;
    private final Map<String, BigDecimal> cache = new ConcurrentHashMap<>();

    public CostRuleService(CostRuleMapper costRuleMapper,
                           OrderMapper orderMapper,
                           OrderItemMapper orderItemMapper,
                           KitchenService kitchenService) {
        this.costRuleMapper = costRuleMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.kitchenService = kitchenService;
    }

    @PostConstruct
    @Transactional
    public void seedDefaults() {
        for (Map.Entry<String, BigDecimal> entry : DEFAULTS.entrySet()) {
            Long count = costRuleMapper.selectCount(new LambdaQueryWrapper<CostRuleDO>()
                    .eq(CostRuleDO::getRuleKey, entry.getKey()));
            if (count == null || count == 0) {
                CostRuleDO rule = new CostRuleDO();
                rule.setRuleKey(entry.getKey());
                rule.setRuleValue(entry.getValue());
                rule.setDescription(describe(entry.getKey()));
                costRuleMapper.insert(rule);
            }
        }
        reload();
    }

    public synchronized void reload() {
        cache.clear();
        for (CostRuleDO rule : costRuleMapper.selectList(null)) {
            cache.put(rule.getRuleKey(), rule.getRuleValue());
        }
    }

    public BigDecimal value(String key) {
        if (cache.isEmpty()) {
            reload();
        }
        BigDecimal value = cache.get(key);
        if (value == null) {
            value = DEFAULTS.get(key);
        }
        if (value == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "缺少成本参数：" + key);
        }
        return value;
    }

    public List<CostRuleDO> listRules() {
        List<CostRuleDO> rules = costRuleMapper.selectList(null);
        rules.sort((a, b) -> a.getRuleKey().compareTo(b.getRuleKey()));
        return rules;
    }

    @Transactional
    public void updateRule(String key, BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "参数值不能为负");
        }
        CostRuleDO rule = costRuleMapper.selectOne(new LambdaQueryWrapper<CostRuleDO>()
                .eq(CostRuleDO::getRuleKey, key));
        if (rule == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "成本参数不存在：" + key);
        }
        rule.setRuleValue(value);
        costRuleMapper.updateById(rule);
        reload();
    }

    /** 按日成本报表。只统计已锁定的订单（未锁单没有实收，也没有履约成本）。 */
    public CostReportResp report(LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now().minusDays(6) : start;
        LocalDate to = end == null ? LocalDate.now() : end;
        if (from.isAfter(to)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        List<OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                .between(OrderDO::getServiceDate, from, to)
                .orderByAsc(OrderDO::getServiceDate));
        List<OrderDO> locked = orders.stream()
                .filter(o -> OrderStatus.valueOf(o.getStatus()).isLocked())
                .toList();
        Map<Long, OrderDO> orderMap = locked.stream()
                .collect(Collectors.toMap(OrderDO::getId, o -> o));
        List<OrderItemDO> items = locked.isEmpty() ? List.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemDO>()
                        .in(OrderItemDO::getOrderId, locked.stream().map(OrderDO::getId).toList()));

        Map<LocalDate, List<OrderDO>> ordersByDate = locked.stream()
                .collect(Collectors.groupingBy(OrderDO::getServiceDate));
        Map<LocalDate, List<OrderItemDO>> itemsByDate = items.stream()
                .filter(i -> orderMap.containsKey(i.getOrderId()))
                .collect(Collectors.groupingBy(i -> orderMap.get(i.getOrderId()).getServiceDate()));

        BigDecimal packagingPerMeal = value(KEY_PACKAGING_PER_MEAL);
        BigDecimal deliveryPerOrder = value(KEY_DELIVERY_PER_ORDER);
        BigDecimal feeRate = value(KEY_PAYMENT_FEE_RATE);
        BigDecimal fixedPerOrder = value(KEY_FIXED_COST_PER_ORDER);

        List<CostReportResp.DayRow> rows = new ArrayList<>();
        BigDecimal theoreticalSum = BigDecimal.ZERO;
        BigDecimal actualSum = BigDecimal.ZERO;
        boolean anyIssue = false;
        for (LocalDate date : new TreeMap<>(ordersByDate).keySet()) {
            List<OrderDO> dayOrders = ordersByDate.get(date);
            List<OrderItemDO> dayItems = itemsByDate.getOrDefault(date, List.of());
            MaterialDemandResp demand = kitchenService.materialDemand(date);
            // 只比「已登记领料」的食材：理论取同一条食材的 BOM 推导成本，实际取其领料成本，
            // 否则会拿全天的理论值去比部分登记的实际值，差异没有意义。
            List<MaterialDemandResp.Item> issued = demand.items().stream()
                    .filter(i -> i.actualCost() != null).toList();
            BigDecimal theoretical = issued.stream()
                    .map(MaterialDemandResp.Item::cost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal actual = issued.stream()
                    .map(MaterialDemandResp.Item::actualCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean hasIssue = !issued.isEmpty();
            theoreticalSum = theoreticalSum.add(theoretical);
            actualSum = actualSum.add(actual);
            anyIssue = anyIssue || hasIssue;
            rows.add(buildRow(date, dayOrders, dayItems,
                    packagingPerMeal, deliveryPerOrder, feeRate, fixedPerOrder,
                    theoretical, actual, hasIssue));
        }

        List<OrderItemDO> allItems = items.stream()
                .filter(i -> orderMap.containsKey(i.getOrderId()))
                .toList();
        CostReportResp.DayRow totals = buildRow(null, locked, allItems,
                packagingPerMeal, deliveryPerOrder, feeRate, fixedPerOrder,
                theoreticalSum, actualSum, anyIssue);
        return new CostReportResp(from, to, rows, totals);
    }

    private CostReportResp.DayRow buildRow(LocalDate date, List<OrderDO> dayOrders, List<OrderItemDO> items,
                                           BigDecimal packagingPerMeal, BigDecimal deliveryPerOrder,
                                           BigDecimal feeRate, BigDecimal fixedPerOrder,
                                           BigDecimal materialTheoretical, BigDecimal materialActual,
                                           boolean hasIssue) {
        int orderCount = dayOrders.size();
        BigDecimal foodCost = BigDecimal.ZERO;
        for (OrderItemDO item : items) {
            foodCost = foodCost.add(item.getCost() == null ? BigDecimal.ZERO : item.getCost());
        }
        // 实收取自订单余额抵扣金额，与账本口径一致
        BigDecimal revenue = dayOrders.stream()
                .map(o -> o.getBalanceAmount() == null ? BigDecimal.ZERO : o.getBalanceAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int meals = items.size();
        // 各项先取整再求和，保证报表上「总成本 = 各分项之和」永远成立
        BigDecimal food = scale2(foodCost);
        BigDecimal packaging = scale2(packagingPerMeal.multiply(BigDecimal.valueOf(meals)));
        BigDecimal delivery = scale2(deliveryPerOrder.multiply(BigDecimal.valueOf(orderCount)));
        BigDecimal paymentFee = scale2(revenue.multiply(feeRate));
        BigDecimal fixed = scale2(fixedPerOrder.multiply(BigDecimal.valueOf(orderCount)));
        BigDecimal totalCost = food.add(packaging).add(delivery).add(paymentFee).add(fixed);
        BigDecimal revenueRounded = scale2(revenue);
        BigDecimal grossProfit = revenueRounded.subtract(totalCost);
        // 先取整再相减，保证「差异 = 实际 − 理论」在报表上恒成立
        BigDecimal materialTheoreticalRounded = scale2(materialTheoretical);
        BigDecimal materialActualRounded = scale2(materialActual);
        BigDecimal materialDiff = hasIssue
                ? materialActualRounded.subtract(materialTheoreticalRounded) : null;
        return new CostReportResp.DayRow(date, orderCount, meals,
                revenueRounded, food, packaging, delivery,
                paymentFee, fixed, totalCost, grossProfit,
                rate(food, revenueRounded), rate(totalCost, revenueRounded),
                materialTheoreticalRounded, materialActualRounded, materialDiff, hasIssue);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return part.divide(whole, 4, RoundingMode.HALF_UP);
    }

    private String describe(String key) {
        return switch (key) {
            case KEY_PACKAGING_PER_MEAL -> "每餐包装成本（元）";
            case KEY_DELIVERY_PER_ORDER -> "每单配送成本（元，含两次配送）";
            case KEY_PAYMENT_FEE_RATE -> "支付手续费率";
            case KEY_FIXED_COST_PER_ORDER -> "每单固定成本分摊（元）";
            case KEY_TARGET_COST_RATE -> "目标成本率";
            default -> key;
        };
    }
}
