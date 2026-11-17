package com.sancan.meal.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.enums.DeliveryBatch;
import com.sancan.meal.menu.enums.MealType;
import com.sancan.meal.menu.service.AllergenResolver;
import com.sancan.meal.order.controller.dto.CreateOrderReq;
import com.sancan.meal.order.controller.dto.OrderDetailResp;
import com.sancan.meal.order.dal.dataobject.DeliveryDO;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.dataobject.OrderItemDO;
import com.sancan.meal.order.dal.dataobject.OrderSnapshotDO;
import com.sancan.meal.order.dal.mapper.DeliveryMapper;
import com.sancan.meal.order.dal.mapper.OrderItemMapper;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.dal.mapper.OrderGroupMapper;
import com.sancan.meal.order.dal.mapper.OrderSnapshotMapper;
import com.sancan.meal.order.enums.OrderStatus;
import com.sancan.meal.plan.dal.dataobject.PlanItemDO;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.service.PlanService;
import com.sancan.meal.plan.service.ProfileService;
import com.sancan.meal.audit.service.AuditLogService;
import com.sancan.meal.plan.review.PlanReviewService;
import com.sancan.meal.pricing.service.DailyPricingService;
import com.sancan.meal.coupon.service.CouponService;
import com.sancan.meal.wallet.service.WalletService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** 订单：确认方案 → 待锁定 → 锁单 → 取消/退款（手册 §5、§8）。 */
@Service
public class OrderService {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderGroupMapper groupMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderSnapshotMapper snapshotMapper;
    private final DeliveryMapper deliveryMapper;
    private final PlanService planService;
    private final ProfileService profileService;
    private final AllergenResolver allergenResolver;
    private final DishVersionMapper dishVersionMapper;
    private final DishMapper dishMapper;
    private final CouponService couponService;
    private final WalletService walletService;
    private final PlanReviewService planReviewService;
    private final AuditLogService auditLogService;
    private final OrderDeadline orderDeadline;

    public OrderService(OrderMapper orderMapper,
                        OrderGroupMapper groupMapper,
                        OrderItemMapper orderItemMapper,
                        OrderSnapshotMapper snapshotMapper,
                        DeliveryMapper deliveryMapper,
                        PlanService planService,
                        ProfileService profileService,
                        AllergenResolver allergenResolver,
                        DishVersionMapper dishVersionMapper,
                        DishMapper dishMapper,
                        CouponService couponService,
                        WalletService walletService,
                        PlanReviewService planReviewService,
                        AuditLogService auditLogService,
                        OrderDeadline orderDeadline) {
        this.orderMapper = orderMapper;
        this.groupMapper = groupMapper;
        this.orderItemMapper = orderItemMapper;
        this.snapshotMapper = snapshotMapper;
        this.deliveryMapper = deliveryMapper;
        this.planService = planService;
        this.profileService = profileService;
        this.allergenResolver = allergenResolver;
        this.dishVersionMapper = dishVersionMapper;
        this.dishMapper = dishMapper;
        this.couponService = couponService;
        this.walletService = walletService;
        this.planReviewService = planReviewService;
        this.auditLogService = auditLogService;
        this.orderDeadline = orderDeadline;
    }

    /**
     * 确认方案并生成订单（待锁定）。
     *
     * <p>规则：只能预订明天及以后；同一用户同一服务日只能有一张有效订单；
     * 含过敏原的餐次直接阻断（手册 §16）。
     */
    /**
     * 下单试算：返回原价、券抵扣与实扣，供小程序确认页展示。
     *
     * <p>券不可用时返回 valid=false 与原因，不抛异常——界面需要提示用户重新确认金额。
     */
    public QuoteResp quote(Long userId, LocalDate serviceDate, Long couponId) {
        if (serviceDate == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "服务日不能为空");
        }
        LocalDate today = orderDeadline.now().toLocalDate();
        if (!serviceDate.isAfter(today)) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "只能预订明天及以后的服务日");
        }
        orderDeadline.requireOpen(serviceDate);

        DailyPricingService.Quote quote = planService.dailyQuote(userId, serviceDate);
        String versionNo = planService.planOfDay(userId, serviceDate).getPricingVersionNo();
        BigDecimal original = BigDecimal.valueOf(quote.dailyPrice());
        BigDecimal discount = BigDecimal.ZERO;
        boolean couponValid = true;
        String message = couponId == null ? null : "已选券";
        if (couponId != null) {
            try {
                discount = couponService.calculateDiscount(userId, couponId, original);
            } catch (ServiceException ex) {
                couponValid = false;
                discount = BigDecimal.ZERO;
                message = ex.getMessage();
            }
        }
        BigDecimal payable = original.subtract(discount).max(BigDecimal.ZERO);
        return new QuoteResp(original, discount, payable, couponValid, message,
                versionNo, quote.needsReview());
    }

    /** 试算结果。 */
    public record QuoteResp(BigDecimal originalAmount, BigDecimal couponDiscount, BigDecimal payable,
                            boolean couponValid, String couponMessage,
                            String pricingVersionNo, boolean needsReview) {
    }

    @Transactional
    public OrderDetailResp create(Long userId, CreateOrderReq req) {
        LocalDate serviceDate = req.serviceDate();
        LocalDate today = orderDeadline.now().toLocalDate();
        if (serviceDate == null || !serviceDate.isAfter(today)) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "只能预订明天及以后的服务日");
        }
        // 服务日前一天 12:00 截止（北京时间）
        orderDeadline.requireOpen(serviceDate);
        Long existing = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getUserId, userId)
                .eq(OrderDO::getServiceDate, serviceDate)
                .eq(OrderDO::getActiveKey, 1));
        if (existing != null && existing > 0) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "该服务日已有有效订单");
        }

        List<PlanItemDO> planItems = planService.itemsOfDay(userId, serviceDate);
        UserProfileDO profile = profileService.find(userId);

        List<DishVersionDO> versions = dishVersionMapper.selectBatchIds(
                planItems.stream().map(PlanItemDO::getDishVersionId).collect(Collectors.toSet()));
        Map<Long, DishVersionDO> versionMap = versions.stream()
                .collect(Collectors.toMap(DishVersionDO::getId, v -> v));
        Map<Long, DishDO> dishMap = dishMapper.selectBatchIds(
                        versions.stream().map(DishVersionDO::getDishId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DishDO::getId, d -> d));

        List<String> allergenHit = new ArrayList<>();
        for (PlanItemDO item : planItems) {
            DishVersionDO version = versionMap.get(item.getDishVersionId());
            List<String> dishAllergens = allergenResolver.forVersion(item.getDishVersionId());
            List<String> conflicts = profile == null ? List.of()
                    : AllergenResolver.conflicts(profile.getAllergies(), dishAllergens);
            if (!conflicts.isEmpty()) {
                DishDO dish = version == null ? null : dishMap.get(version.getDishId());
                allergenHit.add((dish == null ? "未知菜品" : dish.getName()) + "（" + String.join("、", conflicts) + "）");
            }
        }
        if (!allergenHit.isEmpty()) {
            throw new ServiceException(ErrorCode.ORDER_ALLERGEN_CONFLICT,
                    "以下餐次含过敏原，已阻止下单：" + String.join("；", allergenHit));
        }

        // 复核门禁：计划的未处理复核事项与最新报价都要检查，任一存在即拒绝
        var planDO = planService.planOfDay(userId, serviceDate);
        var openReviews = planReviewService.openItems(planDO.getId());
        if (!openReviews.isEmpty()) {
            throw new ServiceException(ErrorCode.PRICING_COST_MISSING,
                    "本单存在未处理复核事项，暂不能下单：" + planReviewService.summary(openReviews));
        }
        // 标价以服务日为单位，由计划绑定的定价版本算出（三餐展示价只是拆分）
        DailyPricingService.Quote quote = planService.dailyQuote(userId, serviceDate);
        BigDecimal originalAmount = BigDecimal.valueOf(quote.dailyPrice());
        if (quote.needsReview()) {
            throw new ServiceException(ErrorCode.PRICING_COST_MISSING,
                    "本单存在待人工复核事项，暂不能下单：" + String.join("；", quote.notes()));
        }

        BigDecimal couponAmount = BigDecimal.ZERO;
        if (req.couponId() != null) {
            couponAmount = couponService.calculateDiscount(userId, req.couponId(), originalAmount);
        }

        OrderDO order = new OrderDO();
        order.setOrderNo("SC" + LocalDateTime.now().format(NO_FORMAT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));
        order.setUserId(userId);
        order.setServiceDate(serviceDate);
        order.setPlanId(planItems.get(0).getPlanId());
        order.setStatus(OrderStatus.PENDING_LOCK.name());
        order.setOriginalAmount(originalAmount.setScale(2, RoundingMode.HALF_UP));
        order.setPricingVersionId(quote.versionId());
        order.setPricingVersionNo(quote.versionNo());
        order.setSourceMenuId(planDO.getSourceMenuId());
        order.setSourceMenuVersion(planDO.getSourceMenuVersion());
        order.setRawPrice(quote.rawAdjustedPrice());
        order.setSwapDifference(quote.swapDifference());
        order.setCouponId(req.couponId());
        order.setCouponAmount(couponAmount);
        order.setBalanceAmount(BigDecimal.ZERO);
        order.setCreditAmount(BigDecimal.ZERO);
        order.setCashAmount(BigDecimal.ZERO);
        order.setPayAmount(BigDecimal.ZERO);
        order.setContactName(req.contactName());
        order.setContactPhone(req.contactPhone());
        order.setAddress(req.address());
        order.setActiveKey(1);
        order.setRemark(req.remark());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "该服务日已有有效订单");
        }

        profileService.saveContact(userId, req.contactName(), req.contactPhone(), req.address());

        // 原子预占：条件更新失败说明券已被占用/过期，抛错回滚整单
        if (req.couponId() != null) {
            couponService.reserve(userId, req.couponId(), order.getId());
        }

        for (PlanItemDO item : planItems) {
            DishVersionDO version = versionMap.get(item.getDishVersionId());
            DishDO dish = version == null ? null : dishMap.get(version.getDishId());
            MealType type = MealType.valueOf(item.getMealType());
            OrderItemDO orderItem = new OrderItemDO();
            orderItem.setOrderId(order.getId());
            orderItem.setMealType(item.getMealType());
            orderItem.setDishVersionId(item.getDishVersionId());
            orderItem.setDishName(dish == null ? "未知菜品" : dish.getName());
            orderItem.setDeliveryBatch(DeliveryBatch.forMealType(type).name());
            orderItem.setWeightG(item.getWeightG());
            orderItem.setProteinG(item.getProteinG());
            orderItem.setKcal(item.getKcal());
            orderItem.setPoints(item.getPoints());
            orderItem.setPrice(switch (type) {
                case BREAKFAST -> quote.breakfastPrice();
                case LUNCH -> quote.lunchPrice();
                case DINNER -> quote.dinnerPrice();
            });
            orderItem.setCost(item.getCost());
            orderItemMapper.insert(orderItem);
        }
        return detail(userId, order.getId());
    }

    public PageResult<OrderDO> page(Long userId, String status, int pageNo, int pageSize) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getUserId, userId)
                .orderByDesc(OrderDO::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(OrderDO::getStatus, status.toUpperCase());
        }
        Page<OrderDO> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    public PageResult<OrderDO> adminPage(String status, LocalDate serviceDate, int pageNo, int pageSize) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                .orderByDesc(OrderDO::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(OrderDO::getStatus, status.toUpperCase());
        }
        if (serviceDate != null) {
            wrapper.eq(OrderDO::getServiceDate, serviceDate);
        }
        Page<OrderDO> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    public OrderDO require(Long orderId) {
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    public OrderDO requireOwned(Long userId, Long orderId) {
        OrderDO order = require(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new ServiceException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    public OrderDetailResp detail(Long userId, Long orderId) {
        return toDetail(requireOwned(userId, orderId));
    }

    public OrderDetailResp adminDetail(Long orderId) {
        return toDetail(require(orderId));
    }

    /** 锁单前取消：券退回，不产生任何资金流水。 */
    @Transactional
    public void cancel(Long userId, Long orderId, String reason) {
        OrderDO order = requireOwned(userId, orderId);
        if (!OrderStatus.PENDING_LOCK.name().equals(order.getStatus())) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID,
                    "已锁定的订单不能直接取消，请走退款流程");
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setActiveKey(null);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);
        couponService.releaseForOrder(order.getCouponId(), order.getId());
        releaseGroupCouponIfAllCancelled(order);
    }

    /** 锁单后退款：按原支付来源逐笔退回。 */
    @Transactional
    public void refund(Long orderId, String reason) {
        OrderDO order = require(orderId);
        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        if (!status.isLocked()) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID,
                    "当前状态不支持退款：" + status.label());
        }
        walletService.refundForOrder(order.getUserId(), orderId, reason);
        order.setStatus(OrderStatus.REFUNDED.name());
        order.setRefundTime(LocalDateTime.now());
        order.setCancelReason(reason);
        // 退款后释放「用户 + 服务日」的唯一槽位，与取消口径一致
        order.setActiveKey(null);
        orderMapper.updateById(order);
        auditLogService.record("ORDER_REFUND", "ORDER", orderId, "订单退款：" + (reason == null ? "" : reason));
    }

    /** 配送清单行（导出 CSV 用）。 */
    public record DeliveryRow(String orderNo, String deliveryBatch, String contactName,
                              String contactPhone, String address, String status) {
    }

    /** 某服务日的配送清单，供导出交给配送人员。 */
    public List<DeliveryRow> deliveryRows(LocalDate serviceDate) {
        List<OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getServiceDate, serviceDate)
                .ne(OrderDO::getStatus, OrderStatus.CANCELLED.name()));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(OrderDO::getId).toList();
        Map<Long, OrderDO> orderMap = orders.stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
        List<DeliveryDO> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<DeliveryDO>()
                .in(DeliveryDO::getOrderId, ids)
                .orderByAsc(DeliveryDO::getDeliveryBatch));
        return deliveries.stream()
                .map(d -> {
                    OrderDO order = orderMap.get(d.getOrderId());
                    return new DeliveryRow(order == null ? "" : order.getOrderNo(),
                            d.getDeliveryBatch(), d.getContactName(), d.getContactPhone(),
                            d.getAddress(), d.getStatus());
                })
                .toList();
    }

    /** 周订单组内所有日订单都取消后，释放组上的券。 */
    private void releaseGroupCouponIfAllCancelled(OrderDO order) {
        if (order.getGroupId() == null) {
            return;
        }
        long active = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getGroupId, order.getGroupId())
                .eq(OrderDO::getActiveKey, 1));
        if (active > 0) {
            return;
        }
        var group = groupMapper.selectById(order.getGroupId());
        if (group != null && group.getCouponId() != null) {
            couponService.releaseForOrder(group.getCouponId(), group.getId());
            group.setStatus("CANCELLED");
            groupMapper.updateById(group);
        }
    }

    /** 读取锁单快照（只读，用于核对历史订单内容）。 */
    public com.sancan.meal.order.dal.dataobject.OrderSnapshotDO snapshot(Long orderId) {
        require(orderId);
        var snapshot = snapshotMapper.selectByOrderId(orderId);
        if (snapshot == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "订单尚未锁单，没有快照");
        }
        return snapshot;
    }

    /**
     * 更新单条配送记录的状态。
     *
     * <p>必须按 deliveryId 精确更新：同一批次可能对应两个实际配送日
     * （如 BATCH_2 的前一晚早餐与当晚晚餐），不能按 orderId + batch 批量完成。
     */
    public void updateDelivery(Long orderId, Long deliveryId, String status) {
        require(orderId);
        DeliveryDO delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null || !delivery.getOrderId().equals(orderId)) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "配送记录不存在或不属于该订单");
        }
        delivery.setStatus(status.toUpperCase());
        if ("DONE".equalsIgnoreCase(status)) {
            delivery.setDeliverTime(LocalDateTime.now());
        } else {
            delivery.setDeliverTime(null);
        }
        deliveryMapper.updateById(delivery);
    }

    private OrderDetailResp toDetail(OrderDO order) {
        List<OrderItemDO> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderId, order.getId()));
        List<OrderDetailResp.Item> itemResps = items.stream()
                .sorted(Comparator.comparing(i -> MealType.valueOf(i.getMealType()).getSort()))
                .map(i -> new OrderDetailResp.Item(
                        i.getMealType(), MealType.valueOf(i.getMealType()).getLabel(),
                        i.getDishVersionId(), i.getDishName(), i.getDeliveryBatch(),
                        i.getWeightG(), i.getProteinG(), i.getKcal(), i.getPoints(), i.getPrice()))
                .toList();
        List<DeliveryDO> deliveries = deliveryMapper.selectList(new LambdaQueryWrapper<DeliveryDO>()
                .eq(DeliveryDO::getOrderId, order.getId()));
        List<OrderDetailResp.Delivery> deliveryResps = deliveries.stream()
                .sorted(Comparator.comparing(DeliveryDO::getDeliveryBatch))
                .map(d -> new OrderDetailResp.Delivery(d.getId(), d.getDeliveryBatch(), d.getStatus(), d.getDeliverTime(),
                        d.getServiceDate(), d.getActualDeliveryDate(), d.getDeliverySlot()))
                .toList();
        long snapshotCount = snapshotMapper.countByOrderId(order.getId());
        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        return new OrderDetailResp(
                order.getId(), order.getOrderNo(), order.getServiceDate(),
                order.getStatus(), status.label(),
                order.getOriginalAmount(), order.getPricingVersionNo(), order.getRawPrice(),
                order.getSwapDifference(), order.getCouponId(), order.getCouponAmount(),
                order.getBalanceAmount(), order.getCreditAmount(), order.getCashAmount(), order.getPayAmount(),
                order.getContactName(), order.getContactPhone(), order.getAddress(),
                order.getCreateTime(), order.getLockTime(), order.getLockFailReason(),
                order.getCancelTime(), order.getCancelReason(),
                status == OrderStatus.PENDING_LOCK,
                snapshotCount > 0,
                !deliveryResps.isEmpty(),
                itemResps, deliveryResps);
    }
}
