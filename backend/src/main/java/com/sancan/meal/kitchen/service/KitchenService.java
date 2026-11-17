package com.sancan.meal.kitchen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.kitchen.controller.dto.KitchenSummaryResp;
import com.sancan.meal.kitchen.controller.dto.MaterialDemandResp;
import com.sancan.meal.kitchen.dal.dataobject.MaterialIssueDO;
import com.sancan.meal.kitchen.dal.dataobject.ProductionBatchDO;
import com.sancan.meal.kitchen.dal.dataobject.SubstitutionDO;
import com.sancan.meal.kitchen.dal.mapper.MaterialIssueMapper;
import com.sancan.meal.kitchen.dal.mapper.ProductionBatchMapper;
import com.sancan.meal.kitchen.dal.mapper.SubstitutionMapper;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import com.sancan.meal.menu.enums.DeliveryBatch;
import com.sancan.meal.menu.enums.MealType;
import com.sancan.meal.menu.service.AllergenResolver;
import com.sancan.meal.menu.service.IngredientCostCalculator;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.dataobject.OrderItemDO;
import com.sancan.meal.order.dal.mapper.OrderItemMapper;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.enums.OrderStatus;
import com.sancan.meal.plan.service.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 厨房生产（手册 §9）。
 *
 * <p>核心口径：**先合并生产，再按用户分装**。生产批次以
 * 「服务日 + 配送批次 + 菜品」为粒度汇总已锁订单，而不是逐单制作。
 */
@Service
public class KitchenService {

    private final ProductionBatchMapper batchMapper;
    private final MaterialIssueMapper issueMapper;
    private final SubstitutionMapper substitutionMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishVersionMapper dishVersionMapper;
    private final DishMapper dishMapper;
    private final RecipeBomMapper recipeBomMapper;
    private final IngredientMapper ingredientMapper;
    private final AllergenResolver allergenResolver;
    private final ProfileService profileService;

    public KitchenService(ProductionBatchMapper batchMapper,
                          MaterialIssueMapper issueMapper,
                          SubstitutionMapper substitutionMapper,
                          OrderMapper orderMapper,
                          OrderItemMapper orderItemMapper,
                          DishVersionMapper dishVersionMapper,
                          DishMapper dishMapper,
                          RecipeBomMapper recipeBomMapper,
                          IngredientMapper ingredientMapper,
                          AllergenResolver allergenResolver,
                          ProfileService profileService) {
        this.batchMapper = batchMapper;
        this.issueMapper = issueMapper;
        this.substitutionMapper = substitutionMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.dishMapper = dishMapper;
        this.recipeBomMapper = recipeBomMapper;
        this.ingredientMapper = ingredientMapper;
        this.allergenResolver = allergenResolver;
        this.profileService = profileService;
    }

    /**
     * 履约口径：生产日 D 的批次 = D 日午餐 + D 日晚餐 + D+1 日早餐。
     * 早餐随前一晚的晚餐批次（BATCH_2）配送，实际配送日 = D。
     */
    private record KitItem(OrderDO order, OrderItemDO item, String batch, LocalDate actualDate) {
    }

    private List<KitItem> fulfillmentItems(LocalDate deliveryDate) {
        List<KitItem> result = new ArrayList<>();
        for (OrderDO order : lockedOrders(deliveryDate)) {
            for (OrderItemDO item : itemsOf(List.of(order))) {
                MealType type = MealType.valueOf(item.getMealType());
                if (type == MealType.BREAKFAST) {
                    continue;
                }
                result.add(new KitItem(order, item, DeliveryBatch.forMealType(type).name(), deliveryDate));
            }
        }
        for (OrderDO order : lockedOrders(deliveryDate.plusDays(1))) {
            for (OrderItemDO item : itemsOf(List.of(order))) {
                if (!"BREAKFAST".equals(item.getMealType())) {
                    continue;
                }
                result.add(new KitItem(order, item, DeliveryBatch.BATCH_2.name(), deliveryDate));
            }
        }
        return result;
    }

    /**
     * 汇总已锁订单生成生产批次。可重复执行：份数按最新订单重算，
     * 已登记的实做与损耗保留。
     */
    @Transactional
    public KitchenSummaryResp generate(LocalDate serviceDate) {
        List<KitItem> kitItems = fulfillmentItems(serviceDate);

        Map<String, int[]> grouped = new LinkedHashMap<>();
        Map<String, String> dishNames = new HashMap<>();
        Map<String, Long> versionIds = new HashMap<>();
        for (KitItem kit : kitItems) {
            OrderItemDO item = kit.item();
            String key = kit.batch() + "#" + item.getDishVersionId();
            grouped.computeIfAbsent(key, k -> new int[]{0, 0})[0]++;
            dishNames.put(key, item.getDishName());
            versionIds.put(key, item.getDishVersionId());
        }

        List<ProductionBatchDO> existing = batchMapper.selectList(new LambdaQueryWrapper<ProductionBatchDO>()
                .eq(ProductionBatchDO::getServiceDate, serviceDate));
        Map<String, ProductionBatchDO> existingByKey = existing.stream()
                .collect(Collectors.toMap(b -> b.getDeliveryBatch() + "#" + b.getDishVersionId(), b -> b));

        for (Map.Entry<String, int[]> entry : grouped.entrySet()) {
            String key = entry.getKey();
            String batch = key.substring(0, key.indexOf('#'));
            Long versionId = versionIds.get(key);
            ProductionBatchDO row = existingByKey.get(key);
            if (row == null) {
                row = new ProductionBatchDO();
                row.setServiceDate(serviceDate);
                row.setDeliveryBatch(batch);
                row.setDishVersionId(versionId);
                row.setDishName(dishNames.get(key));
                row.setPlanQty(entry.getValue()[0]);
                row.setActualQty(0);
                row.setWasteQty(0);
                row.setStatus("PLANNED");
                batchMapper.insert(row);
            } else {
                row.setPlanQty(entry.getValue()[0]);
                row.setDishName(dishNames.get(key));
                batchMapper.updateById(row);
            }
        }

        // 订单取消后不再需要的批次：只删除尚未开工且没有实做记录的
        for (ProductionBatchDO row : existing) {
            String key = row.getDeliveryBatch() + "#" + row.getDishVersionId();
            if (!grouped.containsKey(key)
                    && (row.getActualQty() == null || row.getActualQty() == 0)
                    && (row.getWasteQty() == null || row.getWasteQty() == 0)) {
                batchMapper.deleteById(row.getId());
            }
        }
        return summary(serviceDate);
    }

    /** 生产汇总单 + 分装标签。 */
    public KitchenSummaryResp summary(LocalDate serviceDate) {
        List<ProductionBatchDO> batches = batchMapper.selectList(new LambdaQueryWrapper<ProductionBatchDO>()
                .eq(ProductionBatchDO::getServiceDate, serviceDate)
                .orderByAsc(ProductionBatchDO::getDeliveryBatch)
                .orderByAsc(ProductionBatchDO::getDishName));
        List<KitchenSummaryResp.BatchRow> batchRows = batches.stream()
                .map(b -> new KitchenSummaryResp.BatchRow(b.getId(), b.getDeliveryBatch(),
                        batchLabel(b.getDeliveryBatch()), b.getDishVersionId(), b.getDishName(),
                        nz(b.getPlanQty()), nz(b.getActualQty()), nz(b.getWasteQty()), b.getStatus()))
                .toList();

        List<KitItem> kitItems = fulfillmentItems(serviceDate);
        Map<Long, List<String>> allergens = allergenResolver.byVersionIds(
                kitItems.stream().map(k -> k.item().getDishVersionId()).collect(Collectors.toSet()));
        List<KitchenSummaryResp.LabelRow> labels = kitItems.stream()
                .sorted(Comparator.comparing(KitItem::batch)
                        .thenComparing(k -> MealType.valueOf(k.item().getMealType()).getSort()))
                .map(k -> new KitchenSummaryResp.LabelRow(
                        k.item().getOrderId(), k.order().getOrderNo(),
                        k.batch(), k.item().getMealType(),
                        MealType.valueOf(k.item().getMealType()).getLabel(),
                        k.item().getDishName(), k.item().getWeightG(), k.item().getProteinG(),
                        allergens.getOrDefault(k.item().getDishVersionId(), List.of()),
                        k.order().getContactName()))
                .toList();

        int planQty = batchRows.stream().mapToInt(KitchenSummaryResp.BatchRow::planQty).sum();
        int actualQty = batchRows.stream().mapToInt(KitchenSummaryResp.BatchRow::actualQty).sum();
        int wasteQty = batchRows.stream().mapToInt(KitchenSummaryResp.BatchRow::wasteQty).sum();
        return new KitchenSummaryResp(serviceDate, planQty, actualQty, wasteQty, batchRows, labels);
    }

    /**
     * 食材需求汇总：理论用量 = 生产份数 × BOM 可食生重，
     * 采购重量 = 可食生重 ÷ 可食率，金额按移动加权价折算（手册 §10）。
     */
    public MaterialDemandResp materialDemand(LocalDate serviceDate) {
        List<OrderItemDO> items = fulfillmentItems(serviceDate).stream().map(KitItem::item).toList();
        if (items.isEmpty()) {
            return new MaterialDemandResp(serviceDate, BigDecimal.ZERO, List.of());
        }

        Set<Long> versionIds = items.stream().map(OrderItemDO::getDishVersionId).collect(Collectors.toSet());
        Map<Long, DishVersionDO> versions = dishVersionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(DishVersionDO::getId, v -> v));
        Map<Long, List<RecipeBomDO>> bomByVersion = recipeBomMapper.selectList(
                        new LambdaQueryWrapper<RecipeBomDO>().in(RecipeBomDO::getDishVersionId, versionIds))
                .stream()
                .collect(Collectors.groupingBy(RecipeBomDO::getDishVersionId));

        // 理论用量必须按「个人份量」折算：同一菜品不同用户的克重不同，
        // 直接按标准 BOM 会被系统性低估。
        Map<Long, BigDecimal> edibleByIngredient = new LinkedHashMap<>();
        for (OrderItemDO item : items) {
            DishVersionDO version = versions.get(item.getDishVersionId());
            if (version == null || version.getNetWeightG() == null || version.getNetWeightG() <= 0) {
                continue;
            }
            BigDecimal scale = BigDecimal.valueOf(item.getWeightG() == null ? 0 : item.getWeightG())
                    .divide(BigDecimal.valueOf(version.getNetWeightG()), 6, RoundingMode.HALF_UP);
            for (RecipeBomDO bom : bomByVersion.getOrDefault(version.getId(), List.of())) {
                edibleByIngredient.merge(bom.getIngredientId(),
                        bom.getNetWeightG().multiply(scale), BigDecimal::add);
            }
        }

        Map<Long, IngredientDO> ingredients = edibleByIngredient.isEmpty() ? Map.of()
                : ingredientMapper.selectBatchIds(edibleByIngredient.keySet()).stream()
                        .collect(Collectors.toMap(IngredientDO::getId, i -> i));

        Map<Long, BigDecimal[]> issues = new HashMap<>();
        for (MaterialIssueDO issue : issueMapper.selectList(new LambdaQueryWrapper<MaterialIssueDO>()
                .eq(MaterialIssueDO::getServiceDate, serviceDate))) {
            issues.computeIfAbsent(issue.getIngredientId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            issues.get(issue.getIngredientId())[0] =
                    issues.get(issue.getIngredientId())[0].add(issue.getActualG() == null ? BigDecimal.ZERO : issue.getActualG());
            issues.get(issue.getIngredientId())[1] =
                    issues.get(issue.getIngredientId())[1].add(issue.getWasteG() == null ? BigDecimal.ZERO : issue.getWasteG());
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        List<MaterialDemandResp.Item> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : edibleByIngredient.entrySet()) {
            IngredientDO ingredient = ingredients.get(entry.getKey());
            if (ingredient == null) {
                continue;
            }
            BigDecimal edibleG = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            BigDecimal purchaseG = edibleG.divide(ingredient.getEdibleRate(), 2, RoundingMode.HALF_UP);
            BigDecimal cost = IngredientCostCalculator.cost(ingredient, edibleG);
            totalCost = totalCost.add(cost);
            BigDecimal[] issue = issues.get(entry.getKey());
            BigDecimal actualCost = issue == null || issue[0] == null || issue[0].signum() <= 0
                    ? null
                    : IngredientCostCalculator.cost(ingredient, issue[0]);
            result.add(new MaterialDemandResp.Item(ingredient.getId(), ingredient.getName(),
                    ingredient.getUsageUnit(), edibleG, purchaseG, ingredient.getPurchaseUnit(), cost,
                    issue == null ? null : issue[0], issue == null ? null : issue[1], actualCost));
        }
        result.sort(Comparator.comparing(MaterialDemandResp.Item::ingredientName));
        return new MaterialDemandResp(serviceDate, totalCost.setScale(4, RoundingMode.HALF_UP), result);
    }

    @Transactional
    public void updateBatchStatus(Long batchId, String status) {
        ProductionBatchDO batch = requireBatch(batchId);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("PLANNED", "PREPARING", "DONE").contains(normalized)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "生产状态不正确：" + status);
        }
        batch.setStatus(normalized);
        batchMapper.updateById(batch);
    }

    /** 登记实际领料与损耗。同一批次同一食材重复登记为覆盖。 */
    @Transactional
    public void recordIssue(Long batchId, Long ingredientId, BigDecimal actualG, BigDecimal wasteG, String reason) {
        ProductionBatchDO batch = requireBatch(batchId);
        IngredientDO ingredient = ingredientMapper.selectById(ingredientId);
        if (ingredient == null) {
            throw new ServiceException(ErrorCode.INGREDIENT_NOT_FOUND);
        }
        if (actualG == null || actualG.signum() < 0 || (wasteG != null && wasteG.signum() < 0)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "领料与损耗不能为负");
        }
        MaterialIssueDO issue = issueMapper.selectOne(new LambdaQueryWrapper<MaterialIssueDO>()
                .eq(MaterialIssueDO::getBatchId, batchId)
                .eq(MaterialIssueDO::getIngredientId, ingredientId));
        if (issue == null) {
            issue = new MaterialIssueDO();
            issue.setBatchId(batchId);
            issue.setServiceDate(batch.getServiceDate());
            issue.setDeliveryBatch(batch.getDeliveryBatch());
            issue.setIngredientId(ingredientId);
            issue.setIngredientName(ingredient.getName());
        }
        issue.setActualG(actualG);
        issue.setWasteG(wasteG == null ? BigDecimal.ZERO : wasteG);
        issue.setReason(reason);
        issue.setTheoreticalG(theoreticalFor(batch, ingredientId));
        if (issue.getId() == null) {
            issueMapper.insert(issue);
        } else {
            issueMapper.updateById(issue);
        }
    }

    /** 登记成品产量与报废。 */
    @Transactional
    public void recordOutput(Long batchId, int actualQty, int wasteQty, String remark) {
        ProductionBatchDO batch = requireBatch(batchId);
        if (actualQty < 0 || wasteQty < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "产量与报废不能为负");
        }
        batch.setActualQty(actualQty);
        batch.setWasteQty(wasteQty);
        if (remark != null) {
            batch.setRemark(remark);
        }
        if (actualQty + wasteQty > 0 && !"DONE".equals(batch.getStatus())) {
            batch.setStatus("PREPARING");
        }
        batchMapper.updateById(batch);
    }

    /** 临时替换：记录审批人与通知状态，快照不变。 */
    @Transactional
    public SubstitutionDO substitution(Long orderId, String mealType, Long toDishVersionId,
                                       String reason, Long approvalBy) {
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!OrderStatus.valueOf(order.getStatus()).isLocked()) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID, "只有已锁定的订单才能登记临时替换");
        }
        MealType type;
        try {
            type = MealType.valueOf(mealType == null ? "" : mealType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "餐次类型不正确：" + mealType);
        }
        OrderItemDO item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderId, orderId)
                .eq(OrderItemDO::getMealType, type.name()));
        if (item == null) {
            throw new ServiceException(ErrorCode.ORDER_NOT_FOUND, "订单没有该餐次");
        }
        DishVersionDO target = dishVersionMapper.selectById(toDishVersionId);
        if (target == null) {
            throw new ServiceException(ErrorCode.DISH_NOT_FOUND);
        }
        var profile = profileService.find(order.getUserId());
        List<String> conflicts = profile == null ? List.of()
                : AllergenResolver.conflicts(profile.getAllergies(), allergenResolver.forVersion(target.getId()));
        if (!conflicts.isEmpty()) {
            throw new ServiceException(ErrorCode.ALLERGEN_CONFLICT,
                    "替换目标含用户过敏原：" + String.join("、", conflicts));
        }

        SubstitutionDO record = new SubstitutionDO();
        record.setOrderId(orderId);
        record.setServiceDate(order.getServiceDate());
        record.setMealType(type.name());
        record.setFromDishVersionId(item.getDishVersionId());
        record.setToDishVersionId(target.getId());
        record.setReason(reason);
        record.setApprovalBy(approvalBy);
        record.setApprovalTime(LocalDateTime.now());
        record.setNotifyStatus("PENDING");
        substitutionMapper.insert(record);
        return record;
    }

    @Transactional
    public void markNotified(Long substitutionId) {
        SubstitutionDO record = substitutionMapper.selectById(substitutionId);
        if (record == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "替换记录不存在");
        }
        record.setNotifyStatus("SENT");
        record.setNotifyTime(LocalDateTime.now());
        substitutionMapper.updateById(record);
    }

    public List<SubstitutionDO> substitutions(Long orderId) {
        return substitutionMapper.selectList(new LambdaQueryWrapper<SubstitutionDO>()
                .eq(SubstitutionDO::getOrderId, orderId)
                .orderByDesc(SubstitutionDO::getId));
    }

    // ==================== 内部 ====================

    private ProductionBatchDO requireBatch(Long batchId) {
        ProductionBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new ServiceException(ErrorCode.PRODUCTION_BATCH_NOT_FOUND);
        }
        return batch;
    }

    private BigDecimal theoreticalFor(ProductionBatchDO batch, Long ingredientId) {
        List<RecipeBomDO> bom = recipeBomMapper.selectList(new LambdaQueryWrapper<RecipeBomDO>()
                .eq(RecipeBomDO::getDishVersionId, batch.getDishVersionId())
                .eq(RecipeBomDO::getIngredientId, ingredientId));
        if (bom.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return bom.get(0).getNetWeightG().multiply(BigDecimal.valueOf(nz(batch.getPlanQty())))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<OrderDO> lockedOrders(LocalDate serviceDate) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getServiceDate, serviceDate))
                .stream()
                .filter(o -> OrderStatus.valueOf(o.getStatus()).isLocked())
                .toList();
    }

    private List<OrderItemDO> itemsOf(List<OrderDO> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemDO>()
                .in(OrderItemDO::getOrderId, orders.stream().map(OrderDO::getId).toList()));
    }

    private String batchLabel(String batch) {
        try {
            return DeliveryBatch.valueOf(batch).getLabel();
        } catch (IllegalArgumentException ex) {
            return batch;
        }
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
