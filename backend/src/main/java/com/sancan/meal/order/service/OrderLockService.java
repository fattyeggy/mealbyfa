package com.sancan.meal.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.dal.dataobject.WeekMenuDO;
import com.sancan.meal.menu.service.WeekMenuService;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import com.sancan.meal.menu.dal.mapper.WeekMenuMapper;
import com.sancan.meal.menu.enums.DeliveryBatch;
import com.sancan.meal.menu.enums.MealType;
import com.sancan.meal.plan.dal.dataobject.NutritionPlanDO;
import com.sancan.meal.plan.dal.mapper.NutritionPlanMapper;
import com.sancan.meal.order.dal.dataobject.DeliveryItemDO;
import com.sancan.meal.order.dal.mapper.DeliveryItemMapper;
import com.sancan.meal.order.dal.dataobject.DeliveryDO;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.dataobject.OrderItemDO;
import com.sancan.meal.order.dal.dataobject.OrderSnapshotDO;
import com.sancan.meal.order.dal.mapper.DeliveryMapper;
import com.sancan.meal.order.dal.mapper.OrderGroupMapper;
import com.sancan.meal.order.dal.mapper.OrderItemMapper;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.dal.mapper.OrderSnapshotMapper;
import com.sancan.meal.order.enums.OrderStatus;
import com.sancan.meal.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 锁单事务（手册 §8「锁单技术要求」）。
 *
 * <p>一次锁单同时完成：余额校验 → 扣款 → 券核销 → 订单快照 → 生产需求与配送记录。
 * 任何一步失败整体回滚，订单保持「待锁定」。
 *
 * <p>幂等性来自三层：订单行锁（{@code SELECT ... FOR UPDATE}）、状态机校验、
 * 账本唯一索引 {@code (biz_type, ref_type, ref_id, source)}。
 */
@Service
public class OrderLockService {

    private static final Logger log = LoggerFactory.getLogger(OrderLockService.class);

    public enum Outcome {
        /** 锁定成功。 */
        LOCKED,
        /** 余额不足，已记录失败原因，订单仍为待锁定。 */
        FAILED_INSUFFICIENT,
        /** 不是待锁定状态，跳过。 */
        SKIPPED_NOT_PENDING,
        /** 已经被其他线程锁定。 */
        ALREADY_LOCKED
    }

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderSnapshotMapper snapshotMapper;
    private final DeliveryMapper deliveryMapper;
    private final WalletService walletService;
    private final com.sancan.meal.coupon.service.CouponService couponService;
    private final WeekMenuService weekMenuService;
    private final ObjectMapper objectMapper;
    private final DishVersionMapper dishVersionMapper;
    private final DishMapper dishMapper;
    private final RecipeBomMapper recipeBomMapper;
    private final IngredientMapper ingredientMapper;
    private final NutritionPlanMapper planMapper;
    private final WeekMenuMapper weekMenuMapper;
    private final DeliveryItemMapper deliveryItemMapper;
    private final OrderGroupMapper groupMapper;

    public OrderLockService(OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper,
                            OrderSnapshotMapper snapshotMapper,
                            DeliveryMapper deliveryMapper,
                            WalletService walletService,
                            com.sancan.meal.coupon.service.CouponService couponService,
                            WeekMenuService weekMenuService,
                            ObjectMapper objectMapper,
                            DishVersionMapper dishVersionMapper,
                            DishMapper dishMapper,
                            RecipeBomMapper recipeBomMapper,
                            IngredientMapper ingredientMapper,
                            NutritionPlanMapper planMapper,
                            WeekMenuMapper weekMenuMapper,
                            DeliveryItemMapper deliveryItemMapper,
                            OrderGroupMapper groupMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.snapshotMapper = snapshotMapper;
        this.deliveryMapper = deliveryMapper;
        this.walletService = walletService;
        this.couponService = couponService;
        this.weekMenuService = weekMenuService;
        this.objectMapper = objectMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.dishMapper = dishMapper;
        this.recipeBomMapper = recipeBomMapper;
        this.ingredientMapper = ingredientMapper;
        this.planMapper = planMapper;
        this.weekMenuMapper = weekMenuMapper;
        this.deliveryItemMapper = deliveryItemMapper;
        this.groupMapper = groupMapper;
    }

    @Transactional
    public Outcome lockOne(Long orderId) {
        // 行锁：并发调用时只有一个线程能拿到待锁定状态的订单
        OrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            return Outcome.SKIPPED_NOT_PENDING;
        }
        if (!OrderStatus.PENDING_LOCK.name().equals(order.getStatus())) {
            return Outcome.ALREADY_LOCKED;
        }

        BigDecimal payable = order.getOriginalAmount()
                .subtract(order.getCouponAmount() == null ? BigDecimal.ZERO : order.getCouponAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        // 预检余额：不足时只落失败原因，不触发扣款异常，避免事务被标记回滚
        if (!walletService.canPay(order.getUserId(), payable)) {
            BigDecimal balance = walletService.view(order.getUserId()).balance();
            order.setLockFailReason("账户余额不足：应付 " + payable + " 元，当前余额 " + balance + " 元");
            orderMapper.updateById(order);
            return Outcome.FAILED_INSUFFICIENT;
        }

        WalletService.Deduction deduction = walletService.deductForOrder(order.getUserId(), orderId, payable);
        order.setBalanceAmount(deduction.total());
        order.setCreditAmount(deduction.creditAmount());
        order.setCashAmount(deduction.cashAmount());
        order.setPayAmount(BigDecimal.ZERO);

        if (order.getCouponId() != null) {
            // 只允许本订单的 RESERVED → USED；失败则整笔锁单回滚
            couponService.consume(order.getCouponId(), orderId);
        }

        createSnapshot(order, payable);

        order.setStatus(OrderStatus.LOCKED.name());
        order.setLockTime(LocalDateTime.now());
        order.setLockFailReason(null);
        orderMapper.updateById(order);

        createDeliveries(order);
        consumeGroupCoupon(order);
        log.info("锁单成功 orderNo={} 服务日={} 应付={} 赠送抵扣={} 现金抵扣={}",
                order.getOrderNo(), order.getServiceDate(), payable,
                deduction.creditAmount(), deduction.cashAmount());
        return Outcome.LOCKED;
    }

    /**
     * 快照：读取「计划绑定的来源菜单」，而不是当前最新发布菜单；
     * 内容包含菜品版本、BOM、营养、过敏原、保存复热、成本与定价明细。
     * 只插入，不更新、不删除。
     */
    private void createSnapshot(OrderDO order, BigDecimal payable) {
        List<OrderItemDO> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderId, order.getId()));
        List<Map<String, Object>> itemPayload = new ArrayList<>();
        for (OrderItemDO item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mealType", item.getMealType());
            row.put("dishVersionId", item.getDishVersionId());
            row.put("dishName", item.getDishName());
            row.put("deliveryBatch", item.getDeliveryBatch());
            row.put("weightG", item.getWeightG());
            row.put("proteinG", item.getProteinG());
            row.put("kcal", item.getKcal());
            row.put("points", item.getPoints());
            row.put("price", item.getPrice());
            row.put("cost", item.getCost());
            DishVersionDO version = dishVersionMapper.selectById(item.getDishVersionId());
            if (version != null) {
                row.put("nutrition", Map.of(
                        "netWeightG", version.getNetWeightG(),
                        "kcal", version.getKcal(),
                        "proteinG", version.getProteinG(),
                        "carbG", version.getCarbG(),
                        "fatG", version.getFatG()));
                DishDO dish = dishMapper.selectById(version.getDishId());
                row.put("storageMethod", dish == null ? null : dish.getStorageMethod());
                row.put("reheatMethod", dish == null ? null : dish.getReheatMethod());
                row.put("extraAllergens", dish == null ? null : dish.getExtraAllergens());
                List<Map<String, Object>> bom = recipeBomMapper.selectList(
                                new LambdaQueryWrapper<RecipeBomDO>().eq(RecipeBomDO::getDishVersionId, version.getId()))
                        .stream().map(b -> {
                            IngredientDO ingredient = ingredientMapper.selectById(b.getIngredientId());
                            Map<String, Object> line = new LinkedHashMap<>();
                            line.put("ingredientId", b.getIngredientId());
                            line.put("ingredientName", ingredient == null ? null : ingredient.getName());
                            line.put("netWeightG", b.getNetWeightG());
                            line.put("allergens", ingredient == null ? null : ingredient.getAllergens());
                            return line;
                        }).toList();
                row.put("bom", bom);
            }
            itemPayload.add(row);
        }

        // 优先读订单自身保存的来源菜单：下单后即使换菜单/重建计划，旧订单仍锁到原菜单
        WeekMenuDO menu = order.getSourceMenuId() == null
                ? null : weekMenuMapper.selectById(order.getSourceMenuId());
        if (menu == null) {
            // 兼容历史订单：再回退到计划来源，最后才用当周已发布菜单
            NutritionPlanDO plan = planMapper.selectOne(new LambdaQueryWrapper<NutritionPlanDO>()
                    .eq(NutritionPlanDO::getUserId, order.getUserId())
                    .eq(NutritionPlanDO::getWeekStart, order.getServiceDate().with(DayOfWeek.MONDAY)));
            if (plan != null && plan.getSourceMenuId() != null) {
                menu = weekMenuMapper.selectById(plan.getSourceMenuId());
            }
        }
        if (menu == null) {
            menu = weekMenuService.findPublishedByWeekStart(order.getServiceDate().with(DayOfWeek.MONDAY));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("serviceDate", order.getServiceDate().toString());
        payload.put("menuId", menu == null ? null : menu.getId());
        payload.put("menuVersion", menu == null ? null : menu.getVersion());
        payload.put("menuTheme", menu == null ? null : menu.getThemeName());
        payload.put("pricingVersionNo", order.getPricingVersionNo());
        payload.put("dailyPrice", order.getOriginalAmount());
        payload.put("rawPrice", order.getRawPrice());
        payload.put("swapDifference", order.getSwapDifference());
        payload.put("originalAmount", order.getOriginalAmount());
        payload.put("couponAmount", order.getCouponAmount());
        payload.put("payable", payable);
        payload.put("lockedAt", LocalDateTime.now().toString());
        payload.put("items", itemPayload);

        OrderSnapshotDO snapshot = new OrderSnapshotDO();
        snapshot.setOrderId(order.getId());
        snapshot.setMenuId(menu == null ? null : menu.getId());
        snapshot.setMenuVersion(menu == null ? null : menu.getVersion());
        snapshot.setTotalPrice(order.getOriginalAmount());
        try {
            snapshot.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new ServiceException(com.sancan.meal.common.exception.ErrorCode.INTERNAL_ERROR,
                    "订单快照生成失败：" + ex.getMessage());
        }
        snapshot.setCreateTime(LocalDateTime.now());
        snapshotMapper.insert(snapshot);
    }

    /** 周订单组：首次锁单时核销组上的券（一组只核销一次）。 */
    private void consumeGroupCoupon(OrderDO order) {
        if (order.getGroupId() == null) {
            return;
        }
        var group = groupMapper.selectById(order.getGroupId());
        if (group == null || group.getCouponId() == null) {
            return;
        }
        var coupon = couponService.get(group.getCouponId());
        if (coupon != null && "RESERVED".equals(coupon.getStatus())) {
            couponService.consume(group.getCouponId(), group.getId());
        }
    }

    /**
     * 配送：按餐次归属批次与实际配送日。
     * D 日早餐随 D-1 晚餐批次；D 日午餐/D 日晚餐分别在 D 日两个批次。
     * 明细写入 meal_delivery_item，跨日早餐由这张表表达。
     */
    private void createDeliveries(OrderDO order) {
        List<OrderItemDO> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderId, order.getId()));
        for (OrderItemDO item : items) {
            MealType type = MealType.valueOf(item.getMealType());
            DeliveryBatch batch = DeliveryBatch.forMealType(type);
            LocalDate actualDate = type == MealType.BREAKFAST
                    ? order.getServiceDate().minusDays(1)
                    : order.getServiceDate();

            DeliveryDO delivery = deliveryMapper.selectOne(new LambdaQueryWrapper<DeliveryDO>()
                    .eq(DeliveryDO::getOrderId, order.getId())
                    .eq(DeliveryDO::getDeliveryBatch, batch.name())
                    .eq(DeliveryDO::getActualDeliveryDate, actualDate));
            if (delivery == null) {
                delivery = new DeliveryDO();
                delivery.setOrderId(order.getId());
                delivery.setDeliveryBatch(batch.name());
                delivery.setServiceDate(order.getServiceDate());
                delivery.setActualDeliveryDate(actualDate);
                delivery.setDeliverySlot(type.name());
                delivery.setStatus("PENDING");
                delivery.setContactName(order.getContactName());
                delivery.setContactPhone(order.getContactPhone());
                delivery.setAddress(order.getAddress());
                deliveryMapper.insert(delivery);
            }
            Long exists = deliveryItemMapper.selectCount(new LambdaQueryWrapper<DeliveryItemDO>()
                    .eq(DeliveryItemDO::getDeliveryId, delivery.getId())
                    .eq(DeliveryItemDO::getOrderItemId, item.getId()));
            if (exists == null || exists == 0) {
                DeliveryItemDO line = new DeliveryItemDO();
                line.setDeliveryId(delivery.getId());
                line.setOrderId(order.getId());
                line.setOrderItemId(item.getId());
                line.setMealType(item.getMealType());
                line.setServiceDate(order.getServiceDate());
                deliveryItemMapper.insert(line);
            }
        }
    }
}
