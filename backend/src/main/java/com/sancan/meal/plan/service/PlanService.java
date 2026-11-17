package com.sancan.meal.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.config.MealProperties;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.MenuDayDO;
import com.sancan.meal.menu.dal.dataobject.MenuMealDO;
import com.sancan.meal.menu.dal.dataobject.WeekMenuDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.enums.DeliveryBatch;
import com.sancan.meal.menu.enums.MealType;
import com.sancan.meal.menu.service.AllergenResolver;
import com.sancan.meal.menu.service.WeekMenuService;
import com.sancan.meal.plan.controller.dto.PlanWeekResp;
import com.sancan.meal.plan.controller.dto.SwapPreviewResp;
import com.sancan.meal.plan.dal.dataobject.NutritionPlanDO;
import com.sancan.meal.plan.dal.dataobject.PlanItemDO;
import com.sancan.meal.plan.dal.dataobject.PlanReviewDO;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.dal.mapper.NutritionPlanMapper;
import com.sancan.meal.plan.dal.mapper.PlanItemMapper;
import com.sancan.meal.plan.review.PlanReviewService;
import com.sancan.meal.plan.review.ReviewType;
import com.sancan.meal.pricing.dal.dataobject.PricingVersionDO;
import com.sancan.meal.pricing.dal.dataobject.PricingVersionSwapDO;
import com.sancan.meal.pricing.service.DailyPricingService;
import com.sancan.meal.pricing.service.PricingVersionService;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 个人营养计划（手册 §5 + 计价规则 V1.0 + P4.6 复核门禁）。
 *
 * <p>关键口径：
 * <ul>
 *   <li>原始目标蛋白不做夹取，越界写入 PROTEIN_RANGE 复核事项；</li>
 *   <li>份量缩放触界写 SCALE_CLAMP，不静默改变目标；</li>
 *   <li>复核事实落 meal_plan_review，计划上的 needs_review 只是汇总；</li>
 *   <li>存在未处理复核事项时禁止下单。</li>
 * </ul>
 */
@Service
public class PlanService {

    private static final BigDecimal MIN_SCALE = new BigDecimal("0.50");
    private static final BigDecimal MAX_SCALE = new BigDecimal("2.00");
    private static final BigDecimal TEN = BigDecimal.TEN;

    private final NutritionPlanMapper nutritionPlanMapper;
    private final PlanItemMapper planItemMapper;
    private final WeekMenuService weekMenuService;
    private final DishVersionMapper dishVersionMapper;
    private final DishMapper dishMapper;
    private final AllergenResolver allergenResolver;
    private final PricingVersionService pricingVersionService;
    private final DailyPricingService dailyPricingService;
    private final ProfileService profileService;
    private final NutritionTargetCalculator targetCalculator;
    private final PlanReviewService planReviewService;
    private final MealProperties properties;
    private final ComponentPortioner componentPortioner;
    @org.springframework.beans.factory.annotation.Value("${meal.plan.max-meal-weight-g:0}")
    private int maxMealWeightG;

    public PlanService(NutritionPlanMapper nutritionPlanMapper,
                       PlanItemMapper planItemMapper,
                       WeekMenuService weekMenuService,
                       DishVersionMapper dishVersionMapper,
                       DishMapper dishMapper,
                       AllergenResolver allergenResolver,
                       PricingVersionService pricingVersionService,
                       DailyPricingService dailyPricingService,
                       ProfileService profileService,
                       NutritionTargetCalculator targetCalculator,
                       PlanReviewService planReviewService,
                       MealProperties properties,
                                ComponentPortioner componentPortioner) {
        this.nutritionPlanMapper = nutritionPlanMapper;
        this.planItemMapper = planItemMapper;
        this.weekMenuService = weekMenuService;
        this.dishVersionMapper = dishVersionMapper;
        this.dishMapper = dishMapper;
        this.allergenResolver = allergenResolver;
        this.pricingVersionService = pricingVersionService;
        this.dailyPricingService = dailyPricingService;
        this.profileService = profileService;
        this.targetCalculator = targetCalculator;
        this.planReviewService = planReviewService;
        this.properties = properties;
        this.componentPortioner = componentPortioner;
    }

    private record Scaled(int weightG, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG,
                          int kcal, BigDecimal cost, boolean clamped) {
    }

    @Transactional
    public PlanWeekResp generate(Long userId, LocalDate anyDateInWeek) {
        UserProfileDO profile = profileService.requireCompleted(userId);
        LocalDate weekStart = anyDateInWeek.with(DayOfWeek.MONDAY);
        WeekMenuDO menu = weekMenuService.findPublishedByWeekStart(weekStart);
        if (menu == null) {
            throw new ServiceException(ErrorCode.MENU_NOT_FOUND,
                    "该周菜单尚未发布，暂时无法生成个人计划：" + weekStart);
        }
        PricingVersionDO pricingVersion = pricingVersionService.active();
        NutritionTargetCalculator.Target target = targetCalculator.calculate(profile);
        BigDecimal rawProtein = target.dailyProteinG().setScale(2, RoundingMode.HALF_UP);
        int plannedProtein = rawProtein.setScale(0, RoundingMode.HALF_UP).intValueExact();

        int minProtein = pricingVersion.getMinTargetProteinG() == null ? 80 : pricingVersion.getMinTargetProteinG();
        int maxProtein = pricingVersion.getMaxTargetProteinG() == null ? 220 : pricingVersion.getMaxTargetProteinG();
        boolean proteinOutOfRange = plannedProtein < minProtein || plannedProtein > maxProtein;
        // 新建档（营养模型 v1）直接使用归档后的蛋白档位；老档案仍走原逻辑
        boolean useTier = profile.getTargetProteinTier() != null;
        int usedProtein = useTier ? profile.getTargetProteinTier()
                : (proteinOutOfRange
                    ? Math.min(maxProtein, Math.max(minProtein, plannedProtein)) : plannedProtein);
        if (useTier) {
            proteinOutOfRange = false;
        }
        DailyPricingService.Quote baseQuote = dailyPricingService.quote(pricingVersion, usedProtein, BigDecimal.ZERO);

        List<MenuDayDO> days = weekMenuService.listDays(menu.getId());
        List<MenuMealDO> menuMeals = weekMenuService.listMeals(menu.getId());
        if (menuMeals.isEmpty()) {
            throw new ServiceException(ErrorCode.MENU_STATUS_INVALID, "该周菜单没有配置餐次");
        }
        Map<Long, List<MenuMealDO>> mealsByDay = menuMeals.stream()
                .collect(Collectors.groupingBy(MenuMealDO::getMenuDayId));

        NutritionPlanDO previous = findLatestPlan(userId, weekStart);
        int version = previous == null ? 1 : previous.getVersion() + 1;
        if (previous != null) {
            planItemMapper.delete(new LambdaQueryWrapper<PlanItemDO>()
                    .eq(PlanItemDO::getPlanId, previous.getId()));
            nutritionPlanMapper.deleteById(previous.getId());
        }

        NutritionPlanDO plan = new NutritionPlanDO();
        plan.setUserId(userId);
        plan.setWeekStart(weekStart);
        plan.setTargetProteinG(BigDecimal.valueOf(usedProtein));
        plan.setRawTargetProteinG(rawProtein);
        plan.setTargetKcal(profile.getTargetKcal() != null ? profile.getTargetKcal() : target.dailyKcal());
        plan.setTargetFatG(profile.getTargetFatG());
        plan.setTargetCarbG(profile.getTargetCarbG());
        plan.setProteinTier(profile.getTargetProteinTier());
        plan.setConfigVersion(profile.getNutritionConfigVersion());
        plan.setDailyPoints(BigDecimal.valueOf(usedProtein).divide(TEN, 2, RoundingMode.HALF_UP));
        plan.setPricingVersionId(pricingVersion.getId());
        plan.setPricingVersionNo(pricingVersion.getVersionNo());
        plan.setSourceMenuId(menu.getId());
        plan.setSourceMenuVersion(menu.getVersion());
        plan.setStatus("ACTIVE");
        plan.setVersion(version);
        plan.setNeedsReview(0);
        nutritionPlanMapper.insert(plan);

        List<PlanReviewService.Draft> drafts = new ArrayList<>();
        if (proteinOutOfRange) {
            drafts.add(PlanReviewService.Draft.planLevel(ReviewType.PROTEIN_RANGE.name(),
                    "原始目标 " + rawProtein + "g 超出允许范围 " + minProtein + "–" + maxProtein
                            + "g，已按 " + usedProtein + "g 生成草稿"));
        }
        for (String note : baseQuote.notes()) {
            drafts.add(PlanReviewService.Draft.planLevel(ReviewType.PRICE_FLOOR.name(), note));
        }

        boolean componentScalingUsed = false;
        String userAllergies = profile.getAllergies();
        for (MenuDayDO day : days) {
            for (MenuMealDO menuMeal : mealsByDay.getOrDefault(day.getId(), List.of())) {
                MealType mealType = MealType.valueOf(menuMeal.getMealType());
                DishVersionDO dishVersion = dishVersionMapper.selectById(menuMeal.getDishVersionId());
                if (dishVersion == null) {
                    continue;
                }
                BigDecimal mealTarget = targetCalculator.mealProtein(BigDecimal.valueOf(usedProtein), mealType.name());
                BigDecimal carbTarget = plan.getTargetCarbG() == null ? null
                        : BigDecimal.valueOf(plan.getTargetCarbG())
                            .multiply(BigDecimal.valueOf(targetCalculator.share(mealType.name())))
                            .setScale(2, RoundingMode.HALF_UP);
                BigDecimal fatTarget = plan.getTargetFatG() == null ? null
                        : BigDecimal.valueOf(plan.getTargetFatG())
                            .multiply(BigDecimal.valueOf(targetCalculator.share(mealType.name())))
                            .setScale(2, RoundingMode.HALF_UP);
                Scaled scaled = null;
                if (carbTarget != null && fatTarget != null) {
                    ComponentPortioner.Scaled component =
                            componentPortioner.scale(dishVersion, mealTarget, carbTarget, fatTarget, maxMealWeightG);
                    if (component != null) {
                        componentScalingUsed = true;
                        scaled = new Scaled(component.weightG(), component.proteinG(), component.carbG(),
                                component.fatG(), component.kcal(), component.cost(),
                                component.clamped() || component.capped());
                    }
                }
                if (scaled == null) {
                    scaled = scale(dishVersion, mealTarget);
                }
                if (scaled.clamped()) {
                    drafts.add(PlanReviewService.Draft.mealLevel(ReviewType.SCALE_CLAMP.name(),
                            day.getServiceDate(), mealType.name(),
                            "组件缩放超出允许范围（0.30–2.50 倍）"));
                }
                List<String> conflicts = AllergenResolver.conflicts(userAllergies,
                        allergenResolver.forVersion(dishVersion.getId()));

                PlanItemDO item = new PlanItemDO();
                item.setPlanId(plan.getId());
                item.setUserId(userId);
                item.setServiceDate(day.getServiceDate());
                item.setMealType(mealType.name());
                item.setDishVersionId(dishVersion.getId());
                item.setBaseDishVersionId(dishVersion.getId());
                item.setTargetProteinG(mealTarget);
                item.setWeightG(scaled.weightG());
                item.setProteinG(scaled.proteinG());
                item.setCarbG(scaled.carbG());
                item.setFatG(scaled.fatG());
                item.setKcal(scaled.kcal());
                item.setPoints(points(scaled.proteinG()));
                item.setPrice(displayPrice(baseQuote, mealType));
                item.setCost(scaled.cost());
                item.setBaseCost(scaled.cost());
                item.setAllergenConflict(conflicts.isEmpty() ? 0 : 1);
                planItemMapper.insert(item);
            }
        }
        // 全天约束只在组件化配餐时校验；老菜单没有组件数据，回退算法不应触发复核
        Map<LocalDate, List<PlanItemDO>> byDay = listItems(plan.getId()).stream()
                .collect(Collectors.groupingBy(PlanItemDO::getServiceDate));
        for (Map.Entry<LocalDate, List<PlanItemDO>> entry : byDay.entrySet()) {
            if (!componentScalingUsed) {
                break;
            }
            int kcal = entry.getValue().stream().mapToInt(i -> i.getKcal() == null ? 0 : i.getKcal()).sum();
            double protein = sum(entry.getValue(), PlanItemDO::getProteinG);
            double carb = sum(entry.getValue(), PlanItemDO::getCarbG);
            double fat = sum(entry.getValue(), PlanItemDO::getFatG);
            String deviation = dailyDeviation(kcal, protein, carb, fat, plan.getTargetKcal(),
                    usedProtein, plan.getTargetCarbG(), plan.getTargetFatG());
            if (deviation != null) {
                drafts.add(PlanReviewService.Draft.mealLevel(ReviewType.MACRO_DEVIATION.name(),
                        entry.getKey(), "ALL", deviation));
            }
        }
        planReviewService.addAll(plan.getId(), userId, drafts);
        return toWeekResp(plan, listItems(plan.getId()), profile);
    }

    public PlanWeekResp week(Long userId, LocalDate anyDateInWeek) {
        UserProfileDO profile = profileService.find(userId);
        LocalDate weekStart = anyDateInWeek.with(DayOfWeek.MONDAY);
        NutritionPlanDO plan = findLatestPlan(userId, weekStart);
        if (plan == null) {
            throw new ServiceException(ErrorCode.PLAN_NOT_FOUND, "该周尚未生成个人计划");
        }
        return toWeekResp(plan, listItems(plan.getId()), profile);
    }

    public PlanWeekResp.Day day(Long userId, LocalDate serviceDate) {
        return week(userId, serviceDate).days().stream()
                .filter(d -> d.serviceDate().equals(serviceDate))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.PLAN_NOT_FOUND,
                        "该服务日没有计划餐次：" + serviceDate));
    }

    public List<PlanItemDO> itemsOfDay(Long userId, LocalDate serviceDate) {
        NutritionPlanDO plan = planOfDay(userId, serviceDate);
        List<PlanItemDO> items = planItemMapper.selectList(new LambdaQueryWrapper<PlanItemDO>()
                .eq(PlanItemDO::getPlanId, plan.getId())
                .eq(PlanItemDO::getServiceDate, serviceDate)
                .orderByAsc(PlanItemDO::getMealType));
        if (items.isEmpty()) {
            throw new ServiceException(ErrorCode.PLAN_ITEM_NOT_FOUND, "该服务日没有计划餐次：" + serviceDate);
        }
        return items;
    }

    public NutritionPlanDO planOfDay(Long userId, LocalDate serviceDate) {
        LocalDate weekStart = serviceDate.with(DayOfWeek.MONDAY);
        NutritionPlanDO plan = findLatestPlan(userId, weekStart);
        if (plan == null) {
            throw new ServiceException(ErrorCode.PLAN_NOT_FOUND, "该周尚未生成个人计划");
        }
        return plan;
    }

    public DailyPricingService.Quote dailyQuote(Long userId, LocalDate serviceDate) {
        NutritionPlanDO plan = planOfDay(userId, serviceDate);
        PricingVersionDO version = pricingVersionService.require(plan.getPricingVersionId());
        return quoteFor(version, plan, itemsOfDay(userId, serviceDate));
    }

    /** 该餐次可供选择的已审核替换项（P5 小程序下拉用）。 */
    public List<Map<String, Object>> swapOptions(Long userId, LocalDate serviceDate, String mealType) {
        NutritionPlanDO plan = planOfDay(userId, serviceDate);
        PricingVersionDO pricingVersion = pricingVersionService.require(plan.getPricingVersionId());
        MealType type = parseMealType(mealType);
        PlanItemDO item = itemsOfDay(userId, serviceDate).stream()
                .filter(i -> type.name().equals(i.getMealType()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.PLAN_ITEM_NOT_FOUND));

        List<Map<String, Object>> result = new ArrayList<>();
        for (PricingVersionSwapDO option : pricingVersionService.swaps(pricingVersion.getId())) {
            if (!type.name().equals(option.getMealType())
                    || !item.getDishVersionId().equals(option.getFromDishVersionId())) {
                continue;
            }
            DishVersionDO toVersion = dishVersionMapper.selectById(option.getToDishVersionId());
            if (toVersion == null) {
                continue;
            }
            DishDO toDish = dishMapper.selectById(toVersion.getDishId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dishVersionId", toVersion.getId());
            row.put("dishName", toDish == null ? "未知菜品" : toDish.getName());
            row.put("standardProteinG", toVersion.getProteinG());
            row.put("standardWeightG", toVersion.getNetWeightG());
            result.add(row);
        }
        return result;
    }

    public SwapPreviewResp previewSwap(Long userId, LocalDate serviceDate, String mealType, Long targetVersionId) {
        UserProfileDO profile = profileService.requireCompleted(userId);
        NutritionPlanDO plan = planOfDay(userId, serviceDate);
        PricingVersionDO pricingVersion = pricingVersionService.require(plan.getPricingVersionId());
        List<PlanItemDO> dayItems = itemsOfDay(userId, serviceDate);
        MealType type = parseMealType(mealType);
        PlanItemDO item = dayItems.stream()
                .filter(i -> type.name().equals(i.getMealType()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.PLAN_ITEM_NOT_FOUND));

        DishVersionDO target = requireVersion(targetVersionId);
        DishDO targetDish = dishMapper.selectById(target.getDishId());
        DishVersionDO current = dishVersionMapper.selectById(item.getDishVersionId());
        DishDO currentDish = current == null ? null : dishMapper.selectById(current.getDishId());

        List<String> conflicts = AllergenResolver.conflicts(profile.getAllergies(),
                allergenResolver.forVersion(target.getId()));
        if (!conflicts.isEmpty()) {
            throw new ServiceException(ErrorCode.ALLERGEN_CONFLICT,
                    "替换目标含过敏原：" + String.join("、", conflicts));
        }
        Optional<PricingVersionSwapDO> option = resolveSwapOption(pricingVersion, type,
                item.getDishVersionId(), target.getId());

        Scaled scaled = scale(target, item.getTargetProteinG());
        // 差价基准永远是「最初默认餐次成本」：连续替换不累加、不覆盖
        BigDecimal baseCost = item.getBaseCost() == null ? item.getCost() : item.getBaseCost();
        BigDecimal newCost = option.isPresent()
                // 已审核选项使用冻结成本，不受后续成本重算或采购价调整影响
                ? scaledFrozenCost(option.get().getToVersionCost(), target.getProteinG(),
                        item.getTargetProteinG())
                : scaled.cost();
        if (newCost == null || newCost.signum() <= 0 || baseCost == null || baseCost.signum() <= 0) {
            throw new ServiceException(ErrorCode.PRICING_COST_MISSING,
                    "替换目标或原餐次缺少有效成本数据，需人工复核后再定价");
        }
        BigDecimal newDifference = dailyPricingService.swapDifference(pricingVersion, baseCost, newCost);
        BigDecimal totalDifference = totalSwapDifference(pricingVersion, dayItems, type, baseCost, newCost);
        DailyPricingService.Quote afterQuote = dailyPricingService.quote(pricingVersion,
                plan.getTargetProteinG().intValue(), totalDifference);
        DailyPricingService.Quote beforeQuote = quoteFor(pricingVersion, plan, dayItems);

        SwapPreviewResp.Snapshot before = new SwapPreviewResp.Snapshot(
                item.getWeightG(), item.getProteinG(), item.getKcal(), item.getPoints(),
                displayPrice(beforeQuote, type), BigDecimal.valueOf(beforeQuote.dailyPrice()));
        SwapPreviewResp.Snapshot after = new SwapPreviewResp.Snapshot(
                scaled.weightG(), scaled.proteinG(), scaled.kcal(), points(scaled.proteinG()),
                displayPrice(afterQuote, type), BigDecimal.valueOf(afterQuote.dailyPrice()));

        List<String> notes = new ArrayList<>(afterQuote.notes());
        if (scaled.clamped()) {
            notes.add("替换后份量超出 " + MIN_SCALE + "–" + MAX_SCALE + " 倍范围，需人工复核");
        }
        return new SwapPreviewResp(serviceDate, type.name(), type.getLabel(),
                item.getDishVersionId(), currentDish == null ? null : currentDish.getName(),
                target.getId(), targetDish == null ? null : targetDish.getName(),
                baseCost, newCost, newDifference, before, after,
                BigDecimal.valueOf(afterQuote.dailyPrice() - beforeQuote.dailyPrice()),
                List.of(), !notes.isEmpty(), notes);
    }

    @Transactional
    public PlanItemDO applySwap(Long userId, LocalDate serviceDate, String mealType, Long targetVersionId) {
        UserProfileDO profile = profileService.requireCompleted(userId);
        NutritionPlanDO plan = planOfDay(userId, serviceDate);
        PricingVersionDO pricingVersion = pricingVersionService.require(plan.getPricingVersionId());
        List<PlanItemDO> dayItems = itemsOfDay(userId, serviceDate);
        MealType type = parseMealType(mealType);
        PlanItemDO item = dayItems.stream()
                .filter(i -> type.name().equals(i.getMealType()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.PLAN_ITEM_NOT_FOUND));

        DishVersionDO target = requireVersion(targetVersionId);
        List<String> conflicts = AllergenResolver.conflicts(profile.getAllergies(),
                allergenResolver.forVersion(target.getId()));
        if (!conflicts.isEmpty()) {
            throw new ServiceException(ErrorCode.ALLERGEN_CONFLICT,
                    "替换目标含过敏原：" + String.join("、", conflicts));
        }
        Optional<PricingVersionSwapDO> option = resolveSwapOption(pricingVersion, type,
                item.getDishVersionId(), target.getId());

        Scaled scaled = scale(target, item.getTargetProteinG());
        // 原始基准不可变：baseCost 只来自计划生成时的默认餐次
        BigDecimal baseCost = item.getBaseCost() == null ? item.getCost() : item.getBaseCost();
        BigDecimal newCost = option.isPresent()
                ? scaledFrozenCost(option.get().getToVersionCost(), target.getProteinG(),
                        item.getTargetProteinG())
                : scaled.cost();
        if (newCost == null || newCost.signum() <= 0 || baseCost == null || baseCost.signum() <= 0) {
            throw new ServiceException(ErrorCode.PRICING_COST_MISSING,
                    "替换目标或原餐次缺少有效成本数据，需人工复核后再定价");
        }

        item.setDishVersionId(target.getId());
        item.setWeightG(scaled.weightG());
        item.setProteinG(scaled.proteinG());
        item.setKcal(scaled.kcal());
        item.setPoints(points(scaled.proteinG()));
        item.setCost(newCost);
        // 注意：绝不覆盖 item.baseCost，否则 A→B→A 无法回到原价
        item.setAllergenConflict(0);
        planItemMapper.updateById(item);

        List<PlanItemDO> refreshed = itemsOfDay(userId, serviceDate);
        DailyPricingService.Quote quote = quoteFor(pricingVersion, plan, refreshed);
        for (PlanItemDO row : refreshed) {
            row.setPrice(displayPrice(quote, MealType.valueOf(row.getMealType())));
            planItemMapper.updateById(row);
        }

        // 重新评估该餐次的份量触界，不覆盖其他来源的复核事项
        List<PlanReviewService.Draft> mealDrafts = new ArrayList<>();
        if (scaled.clamped()) {
            mealDrafts.add(PlanReviewService.Draft.mealLevel(ReviewType.SCALE_CLAMP.name(),
                    serviceDate, type.name(), "替换后份量超出 " + MIN_SCALE + "–" + MAX_SCALE + " 倍允许范围"));
        }
        planReviewService.replaceScope(plan.getId(), userId, serviceDate, type.name(),
                ReviewType.SCALE_CLAMP.name(), mealDrafts);

        List<PlanReviewService.Draft> priceDrafts = new ArrayList<>();
        for (String note : quote.notes()) {
            priceDrafts.add(PlanReviewService.Draft.planLevel(ReviewType.PRICE_FLOOR.name(), note));
        }
        planReviewService.replaceScope(plan.getId(), userId, null, null,
                ReviewType.PRICE_FLOOR.name(), priceDrafts);
        return item;
    }

    /** 取已审核的替换选项；生产开着「仅已审核」时没有选项则拒绝。 */
    private Optional<PricingVersionSwapDO> resolveSwapOption(PricingVersionDO version, MealType mealType,
                                                             Long fromVersionId, Long toVersionId) {
        Optional<PricingVersionSwapDO> option = pricingVersionService.findSwap(
                version.getId(), mealType.name(), fromVersionId, toVersionId);
        if (option.isEmpty() && !properties.getPricing().isAllowUnreviewedSwap()) {
            throw new ServiceException(ErrorCode.PRICING_SWAP_NOT_ALLOWED,
                    "该替换选项尚未审核开放，请联系运营");
        }
        return option;
    }

    /**
     * 把审核时冻结的标准份成本，按用户目标蛋白折算到个人份量。
     * 折算比例与实时缩放保持同一口径（目标蛋白 ÷ 菜品蛋白，夹在 0.5–2.0）。
     */
    private BigDecimal scaledFrozenCost(BigDecimal standardCost, BigDecimal standardProtein,
                                        BigDecimal targetProtein) {
        if (standardCost == null || standardProtein == null || standardProtein.signum() <= 0) {
            return null;
        }
        BigDecimal factor = targetProtein.divide(standardProtein, 4, RoundingMode.HALF_UP);
        if (factor.compareTo(MIN_SCALE) < 0) {
            factor = MIN_SCALE;
        } else if (factor.compareTo(MAX_SCALE) > 0) {
            factor = MAX_SCALE;
        }
        return standardCost.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal totalSwapDifference(PricingVersionDO version, List<PlanItemDO> items,
                                           MealType replacedType, BigDecimal replacedBase,
                                           BigDecimal replacedCost) {
        BigDecimal total = BigDecimal.ZERO;
        for (PlanItemDO item : items) {
            BigDecimal base = MealType.valueOf(item.getMealType()) == replacedType
                    ? replacedBase : (item.getBaseCost() == null ? item.getCost() : item.getBaseCost());
            BigDecimal current = MealType.valueOf(item.getMealType()) == replacedType
                    ? replacedCost : item.getCost();
            if (base == null || current == null) {
                continue;
            }
            BigDecimal diff = dailyPricingService.swapDifference(version, base, current);
            if (diff != null) {
                total = total.add(diff);
            }
        }
        return total;
    }

    private DailyPricingService.Quote quoteFor(PricingVersionDO version, NutritionPlanDO plan,
                                               List<PlanItemDO> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PlanItemDO item : items) {
            BigDecimal base = item.getBaseCost() == null ? item.getCost() : item.getBaseCost();
            if (base == null || item.getCost() == null) {
                continue;
            }
            BigDecimal diff = dailyPricingService.swapDifference(version, base, item.getCost());
            if (diff != null) {
                total = total.add(diff);
            }
        }
        return dailyPricingService.quote(version, plan.getTargetProteinG().intValue(), total);
    }

    private BigDecimal displayPrice(DailyPricingService.Quote quote, MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> quote.breakfastPrice();
            case LUNCH -> quote.lunchPrice();
            case DINNER -> quote.dinnerPrice();
        };
    }

    private double sum(List<PlanItemDO> items, java.util.function.Function<PlanItemDO, BigDecimal> getter) {
        return items.stream().map(getter).filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).sum();
    }

    private String dailyDeviation(int kcal, double protein, double carb, double fat,
                                  Integer targetKcal, int targetProtein, Integer targetCarb, Integer targetFat) {
        if (targetKcal == null || targetKcal <= 0 || targetCarb == null || targetFat == null) {
            return null;
        }
        List<String> issues = new ArrayList<>();
        if (Math.abs(kcal - targetKcal) > targetKcal * 0.05) {
            issues.add("热量 " + kcal + " / 目标 " + targetKcal);
        }
        if (protein < targetProtein * 0.95) {
            issues.add("蛋白 " + Math.round(protein) + "g / 目标 " + targetProtein + "g");
        }
        if (Math.abs(carb - targetCarb) > targetCarb * 0.10) {
            issues.add("碳水 " + Math.round(carb) + "g / 目标 " + targetCarb + "g");
        }
        if (Math.abs(fat - targetFat) > targetFat * 0.10) {
            issues.add("脂肪 " + Math.round(fat) + "g / 目标 " + targetFat + "g");
        }
        return issues.isEmpty() ? null : "全天偏差：" + String.join("；", issues);
    }

    private Scaled scale(DishVersionDO version, BigDecimal targetProtein) {
        BigDecimal dishProtein = version.getProteinG() == null ? BigDecimal.ZERO : version.getProteinG();
        BigDecimal factor = BigDecimal.ONE;
        boolean clamped = false;
        if (dishProtein.signum() > 0) {
            factor = targetProtein.divide(dishProtein, 4, RoundingMode.HALF_UP);
            if (factor.compareTo(MIN_SCALE) < 0) {
                factor = MIN_SCALE;
                clamped = true;
            } else if (factor.compareTo(MAX_SCALE) > 0) {
                factor = MAX_SCALE;
                clamped = true;
            }
        }
        int baseWeight = version.getNetWeightG() == null ? 0 : version.getNetWeightG();
        int baseKcal = version.getKcal() == null ? 0 : version.getKcal();
        BigDecimal protein = dishProtein.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal carb = (version.getCarbG() == null ? BigDecimal.ZERO : version.getCarbG())
                .multiply(factor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fat = (version.getFatG() == null ? BigDecimal.ZERO : version.getFatG())
                .multiply(factor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = (version.getCost() == null ? BigDecimal.ZERO : version.getCost())
                .multiply(factor).setScale(4, RoundingMode.HALF_UP);
        return new Scaled(
                BigDecimal.valueOf(baseWeight).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue(),
                protein, carb, fat,
                BigDecimal.valueOf(baseKcal).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue(),
                cost, clamped);
    }

    private BigDecimal points(BigDecimal proteinG) {
        return proteinG.divide(TEN, 2, RoundingMode.HALF_UP);
    }

    private MealType parseMealType(String mealType) {
        try {
            return MealType.valueOf(mealType == null ? "" : mealType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "餐次类型不正确：" + mealType);
        }
    }

    private DishVersionDO requireVersion(Long versionId) {
        DishVersionDO version = versionId == null ? null : dishVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException(ErrorCode.DISH_NOT_FOUND, "菜品版本不存在：" + versionId);
        }
        return version;
    }

    private NutritionPlanDO findLatestPlan(Long userId, LocalDate weekStart) {
        return nutritionPlanMapper.selectOne(new LambdaQueryWrapper<NutritionPlanDO>()
                .eq(NutritionPlanDO::getUserId, userId)
                .eq(NutritionPlanDO::getWeekStart, weekStart)
                .orderByDesc(NutritionPlanDO::getVersion)
                .last("LIMIT 1"));
    }

    private List<PlanItemDO> listItems(Long planId) {
        return planItemMapper.selectList(new LambdaQueryWrapper<PlanItemDO>()
                .eq(PlanItemDO::getPlanId, planId));
    }

    private PlanWeekResp toWeekResp(NutritionPlanDO plan, List<PlanItemDO> items, UserProfileDO profile) {
        PricingVersionDO version = plan.getPricingVersionId() == null
                ? pricingVersionService.active()
                : pricingVersionService.require(plan.getPricingVersionId());

        Map<Long, DishVersionDO> versions = items.isEmpty() ? Map.of()
                : dishVersionMapper.selectBatchIds(items.stream()
                        .map(PlanItemDO::getDishVersionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DishVersionDO::getId, v -> v));
        Map<Long, DishDO> dishes = versions.isEmpty() ? Map.of()
                : dishMapper.selectBatchIds(versions.values().stream()
                        .map(DishVersionDO::getDishId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DishDO::getId, d -> d));
        Map<Long, List<String>> allergens = allergenResolver.byVersionIds(versions.keySet());

        Map<LocalDate, List<PlanItemDO>> byDate = items.stream()
                .collect(Collectors.groupingBy(PlanItemDO::getServiceDate, LinkedHashMap::new, Collectors.toList()));

        List<String> warnings = new ArrayList<>();
        List<PlanWeekResp.Day> dayResps = new ArrayList<>();
        for (Map.Entry<LocalDate, List<PlanItemDO>> entry : byDate.entrySet()) {
            List<PlanItemDO> dayItems = entry.getValue().stream()
                    .sorted(Comparator.comparing(i -> MealType.valueOf(i.getMealType()).getSort()))
                    .toList();
            DailyPricingService.Quote quote = quoteFor(version, plan, dayItems);
            int weight = 0;
            int kcal = 0;
            BigDecimal protein = BigDecimal.ZERO;
            BigDecimal points = BigDecimal.ZERO;
            List<PlanWeekResp.Meal> meals = new ArrayList<>();
            for (PlanItemDO item : dayItems) {
                DishVersionDO dishVersion = versions.get(item.getDishVersionId());
                DishDO dish = dishVersion == null ? null : dishes.get(dishVersion.getDishId());
                MealType type = MealType.valueOf(item.getMealType());
                List<String> dishAllergens = allergens.getOrDefault(item.getDishVersionId(), List.of());
                List<String> conflicts = profile == null ? List.of()
                        : AllergenResolver.conflicts(profile.getAllergies(), dishAllergens);
                if (!conflicts.isEmpty()) {
                    warnings.add(item.getServiceDate() + " " + type.getLabel() + " 含 " + String.join("、", conflicts));
                }
                meals.add(new PlanWeekResp.Meal(
                        item.getMealType(), type.getLabel(), DeliveryBatch.forMealType(type).name(),
                        item.getDishVersionId(), dishVersion == null ? null : dishVersion.getDishId(),
                        dish == null ? "未知菜品" : dish.getName(),
                        dish == null ? "" : dish.getCategory(),
                        dish == null ? null : dish.getDescription(),
                        dish == null ? null : dish.getImageUrl(),
                        item.getWeightG(), item.getProteinG(), item.getKcal(), item.getPoints(),
                        displayPrice(quote, type), dishAllergens,
                        item.getAllergenConflict() != null && item.getAllergenConflict() == 1,
                        conflicts));
                weight += item.getWeightG() == null ? 0 : item.getWeightG();
                kcal += item.getKcal() == null ? 0 : item.getKcal();
                protein = protein.add(item.getProteinG() == null ? BigDecimal.ZERO : item.getProteinG());
                points = points.add(item.getPoints() == null ? BigDecimal.ZERO : item.getPoints());
            }
            BigDecimal dailyPrice = BigDecimal.valueOf(quote.dailyPrice());
            dayResps.add(new PlanWeekResp.Day(entry.getKey(),
                    entry.getKey().getDayOfWeek().getValue(),
                    weight, protein.setScale(2, RoundingMode.HALF_UP), kcal,
                    points.setScale(2, RoundingMode.HALF_UP),
                    dailyPrice, dailyPrice, quote.basePrice(), quote.extraFee(),
                    quote.rawAdjustedPrice(), quote.swapDifference(), meals));
        }
        BigDecimal weekPrice = dayResps.stream()
                .map(PlanWeekResp.Day::dailyPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PlanReviewDO> openReviews = planReviewService.openItems(plan.getId());
        List<PlanWeekResp.Review> reviews = openReviews.stream()
                .map(r -> new PlanWeekResp.Review(r.getReviewType(), r.getServiceDate(),
                        r.getMealType(), r.getReviewMessage(), r.getStatus()))
                .toList();
        List<String> reviewNotes = openReviews.isEmpty()
                ? List.of() : List.of(planReviewService.summary(openReviews));

        // 汇总标记以复核表为准，避免先查实体再写复核导致的读旧值
        boolean needsReview = !openReviews.isEmpty();
        WeekMenuDO menu = weekMenuService.findPublishedByWeekStart(plan.getWeekStart());
        return new PlanWeekResp(plan.getId(), plan.getWeekStart(), plan.getWeekStart().plusDays(6),
                plan.getTargetProteinG(), plan.getRawTargetProteinG(), plan.getTargetKcal(), plan.getDailyPoints(),
                plan.getPricingVersionNo(), needsReview,
                reviewNotes, reviews, weekPrice.setScale(2, RoundingMode.HALF_UP), dayResps, warnings,
                menu == null ? null : menu.getThemeName(),
                menu == null ? null : menu.getRegion());
    }
}
