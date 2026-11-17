package com.sancan.meal.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.feedback.dal.dataobject.FeedbackDO;
import com.sancan.meal.feedback.dal.mapper.FeedbackMapper;
import com.sancan.meal.feedback.enums.LeftoverRatio;
import com.sancan.meal.menu.enums.DeliveryBatch;
import com.sancan.meal.menu.enums.MealType;
import com.sancan.meal.order.dal.dataobject.DeliveryDO;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.dataobject.OrderItemDO;
import com.sancan.meal.order.dal.mapper.DeliveryMapper;
import com.sancan.meal.order.dal.mapper.OrderItemMapper;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.enums.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 完餐反馈与周复盘（手册 §5「收餐到反馈」）。
 *
 * <p>反馈开放条件：订单已锁定，且该餐次所属配送批次已送达（或服务日已过）。
 * 这样避免用户在对应用户还没收到餐时提交反馈。
 */
@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DeliveryMapper deliveryMapper;

    public FeedbackService(FeedbackMapper feedbackMapper, OrderMapper orderMapper,
                           OrderItemMapper orderItemMapper, DeliveryMapper deliveryMapper) {
        this.feedbackMapper = feedbackMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.deliveryMapper = deliveryMapper;
    }

    /** 单餐反馈入参。 */
    public record SubmitReq(String mealType, String leftoverRatio, Integer satiety,
                            String trainingState, String content) {
    }

    /** 周复盘：按日列出反馈，并给出进食率与平均饱腹感。 */
    public record WeekResp(LocalDate weekStart, LocalDate weekEnd, List<Day> days, Summary summary) {
    }

    public record Day(LocalDate serviceDate, List<Item> meals) {
    }

    public record Item(String mealType, String mealLabel, String leftoverRatio, String leftoverLabel,
                       Integer satiety, String trainingState, String content) {
    }

    public record Summary(int totalMeals, double eatenRatio, BigDecimal avgSatiety, int lowSatietyCount) {
    }

    @Transactional
    public FeedbackDO submit(Long userId, Long orderId, SubmitReq req) {
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!OrderStatus.valueOf(order.getStatus()).isLocked()) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "订单尚未锁定，暂不能反馈");
        }
        MealType mealType = parseMealType(req.mealType());
        OrderItemDO item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderId, orderId)
                .eq(OrderItemDO::getMealType, mealType.name()));
        if (item == null) {
            throw new ServiceException(ErrorCode.ORDER_NOT_FOUND, "订单里没有该餐次");
        }
        requireDelivered(order, mealType);

        Long exists = feedbackMapper.selectCount(new LambdaQueryWrapper<FeedbackDO>()
                .eq(FeedbackDO::getOrderId, orderId)
                .eq(FeedbackDO::getMealType, mealType.name()));
        if (exists != null && exists > 0) {
            throw new ServiceException(ErrorCode.FEEDBACK_DUPLICATED);
        }
        LeftoverRatio ratio;
        try {
            ratio = LeftoverRatio.valueOf(req.leftoverRatio() == null ? "" : req.leftoverRatio().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "剩余比例不正确：" + req.leftoverRatio());
        }
        if (req.satiety() != null && (req.satiety() < 1 || req.satiety() > 5)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "饱腹感需在 1–5 之间");
        }

        FeedbackDO feedback = new FeedbackDO();
        feedback.setOrderId(orderId);
        feedback.setUserId(userId);
        feedback.setServiceDate(order.getServiceDate());
        feedback.setMealType(mealType.name());
        feedback.setLeftoverRatio(ratio.name());
        feedback.setSatiety(req.satiety());
        feedback.setTrainingState(req.trainingState());
        feedback.setContent(req.content());
        feedbackMapper.insert(feedback);
        return feedback;
    }

    public WeekResp week(Long userId, LocalDate anyDate) {
        LocalDate weekStart = anyDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<FeedbackDO> rows = feedbackMapper.selectList(new LambdaQueryWrapper<FeedbackDO>()
                .eq(FeedbackDO::getUserId, userId)
                .between(FeedbackDO::getServiceDate, weekStart, weekEnd)
                .orderByAsc(FeedbackDO::getServiceDate));
        Map<LocalDate, List<FeedbackDO>> byDate = rows.stream()
                .collect(Collectors.groupingBy(FeedbackDO::getServiceDate, LinkedHashMap::new, Collectors.toList()));
        List<Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<FeedbackDO>> entry : byDate.entrySet()) {
            List<Item> items = entry.getValue().stream()
                    .sorted(Comparator.comparing(r -> MealType.valueOf(r.getMealType()).getSort()))
                    .map(r -> new Item(r.getMealType(), MealType.valueOf(r.getMealType()).getLabel(),
                            r.getLeftoverRatio(), LeftoverRatio.valueOf(r.getLeftoverRatio()).getLabel(),
                            r.getSatiety(), r.getTrainingState(), r.getContent()))
                    .toList();
            days.add(new Day(entry.getKey(), items));
        }

        double eatenSum = rows.stream()
                .mapToDouble(r -> LeftoverRatio.valueOf(r.getLeftoverRatio()).eatenRatio())
                .sum();
        double avgEaten = rows.isEmpty() ? 0 : eatenSum / rows.size();
        List<Integer> satieties = rows.stream()
                .map(FeedbackDO::getSatiety).filter(java.util.Objects::nonNull).toList();
        BigDecimal avgSatiety = satieties.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(satieties.stream().mapToInt(Integer::intValue).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP);
        int lowSatiety = (int) satieties.stream().filter(s -> s <= 2).count();
        return new WeekResp(weekStart, weekEnd, days,
                new Summary(rows.size(), BigDecimal.valueOf(avgEaten).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                        avgSatiety, lowSatiety));
    }

    public List<FeedbackDO> adminList(LocalDate serviceDate) {
        LambdaQueryWrapper<FeedbackDO> wrapper = new LambdaQueryWrapper<FeedbackDO>()
                .orderByDesc(FeedbackDO::getId)
                .last("LIMIT 200");
        if (serviceDate != null) {
            wrapper.eq(FeedbackDO::getServiceDate, serviceDate);
        }
        return feedbackMapper.selectList(wrapper);
    }

    /** 餐次所属批次必须已送达，或服务日已过。 */
    private void requireDelivered(OrderDO order, MealType mealType) {
        if (order.getServiceDate().isBefore(LocalDate.now())) {
            return;
        }
        String batch = DeliveryBatch.forMealType(mealType).name();
        // 早餐随前一晚批次配送：实际配送日为服务日 −1；午餐/晚餐为当日
        LocalDate actualDate = mealType == MealType.BREAKFAST
                ? order.getServiceDate().minusDays(1) : order.getServiceDate();
        DeliveryDO delivery = deliveryMapper.selectOne(new LambdaQueryWrapper<DeliveryDO>()
                .eq(DeliveryDO::getOrderId, order.getId())
                .eq(DeliveryDO::getDeliveryBatch, batch)
                .eq(DeliveryDO::getActualDeliveryDate, actualDate));
        if (delivery == null || !"DONE".equals(delivery.getStatus())) {
            throw new ServiceException(ErrorCode.FEEDBACK_MEAL_NOT_DELIVERED);
        }
    }

    private MealType parseMealType(String mealType) {
        try {
            return MealType.valueOf(mealType == null ? "" : mealType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "餐次类型不正确：" + mealType);
        }
    }
}
