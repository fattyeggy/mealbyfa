package com.sancan.meal.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.audit.service.AuditLogService;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.config.MealProperties;
import com.sancan.meal.menu.controller.dto.MenuCheckResp;
import com.sancan.meal.menu.controller.dto.PublicWeekResp;
import com.sancan.meal.menu.controller.dto.WeekMenuDetailResp;
import com.sancan.meal.menu.controller.dto.WeekMenuReqs;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.MenuDayDO;
import com.sancan.meal.menu.dal.dataobject.MenuMealDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.dataobject.WeekMenuDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.MenuDayMapper;
import com.sancan.meal.menu.dal.mapper.MenuMealMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import com.sancan.meal.menu.dal.mapper.WeekMenuMapper;
import com.sancan.meal.menu.enums.DeliveryBatch;
import com.sancan.meal.menu.enums.DishVersionStatus;
import com.sancan.meal.menu.enums.MealType;
import com.sancan.meal.menu.enums.MenuStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每周菜单管理（手册 §6）。
 *
 * <p>核心约束：
 * <ul>
 *   <li>一个自然周固定 7 天 21 个餐次；</li>
 *   <li>菜单版本 = 开始日 + 版本号唯一，发布后不可原地修改；</li>
 *   <li>发布前检查必须通过（人工确认项不阻止发布，但会记录）。</li>
 * </ul>
 */
@Service
public class WeekMenuService {

    /** 未发布时为小程序展示的预计发布时间：该周开始日前的周五 08:00。 */
    private static final int PUBLISH_AHEAD_DAYS = 3;
    private static final LocalTime PUBLISH_TIME = LocalTime.of(8, 0);
    private static final BigDecimal POINTS_PER_GRAM_PROTEIN = BigDecimal.TEN;

    private final WeekMenuMapper weekMenuMapper;
    private final MenuDayMapper menuDayMapper;
    private final MenuMealMapper menuMealMapper;
    private final DishMapper dishMapper;
    private final DishVersionMapper dishVersionMapper;
    private final RecipeBomMapper recipeBomMapper;
    private final IngredientMapper ingredientMapper;
    private final AllergenResolver allergenResolver;
    private final AuditLogService auditLogService;
    private final MealProperties properties;

    public WeekMenuService(WeekMenuMapper weekMenuMapper,
                           MenuDayMapper menuDayMapper,
                           MenuMealMapper menuMealMapper,
                           DishMapper dishMapper,
                           DishVersionMapper dishVersionMapper,
                           RecipeBomMapper recipeBomMapper,
                           IngredientMapper ingredientMapper,
                           AllergenResolver allergenResolver,
                           AuditLogService auditLogService,
                           MealProperties properties) {
        this.weekMenuMapper = weekMenuMapper;
        this.menuDayMapper = menuDayMapper;
        this.menuMealMapper = menuMealMapper;
        this.dishMapper = dishMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.recipeBomMapper = recipeBomMapper;
        this.ingredientMapper = ingredientMapper;
        this.allergenResolver = allergenResolver;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    // ==================== 写侧 ====================

    @Transactional
    public Long create(WeekMenuReqs.Create req) {
        requireMonday(req.startDate(), "开始日期必须是周一");
        if (weekMenuMapper.selectCount(new LambdaQueryWrapper<WeekMenuDO>()
                .eq(WeekMenuDO::getStartDate, req.startDate())
                .eq(WeekMenuDO::getVersion, req.version())) > 0) {
            throw new ServiceException(ErrorCode.MENU_VERSION_DUPLICATED);
        }
        WeekMenuDO menu = new WeekMenuDO();
        menu.setThemeName(req.themeName().trim());
        menu.setRegion(req.region().trim());
        menu.setStartDate(req.startDate());
        menu.setEndDate(req.startDate().plusDays(6));
        menu.setVersion(req.version());
        menu.setStatus(MenuStatus.DRAFT.name());
        menu.setRemark(req.remark());
        weekMenuMapper.insert(menu);
        createDays(menu.getId(), menu.getStartDate());
        return menu.getId();
    }

    @Transactional
    public void updateMeta(Long id, WeekMenuReqs.UpdateMeta req) {
        WeekMenuDO menu = requireDraft(id);
        menu.setThemeName(req.themeName().trim());
        menu.setRemark(req.remark());
        menu.setExpectedCostRate(req.expectedCostRate());
        weekMenuMapper.updateById(menu);
    }

    /**
     * 整批替换 21 个餐次。未提交的餐次视为删除，提交后重算每日合计。
     */
    @Transactional
    public void upsertMeals(Long id, WeekMenuReqs.UpsertMeals req) {
        WeekMenuDO menu = requireDraft(id);
        List<MenuDayDO> days = listDays(id);
        Map<LocalDate, MenuDayDO> dayByDate = days.stream()
                .collect(Collectors.toMap(MenuDayDO::getServiceDate, d -> d));

        Set<String> seen = new LinkedHashSet<>();
        List<MenuMealDO> toInsert = new ArrayList<>();
        for (WeekMenuReqs.Slot slot : req.meals()) {
            MenuDayDO day = dayByDate.get(slot.serviceDate());
            if (day == null) {
                throw new ServiceException(ErrorCode.PLAN_DATE_OUT_OF_RANGE,
                        "服务日不在菜单有效期内：" + slot.serviceDate());
            }
            MealType mealType;
            try {
                mealType = MealType.valueOf(slot.mealType());
            } catch (IllegalArgumentException ex) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "餐次类型不正确：" + slot.mealType());
            }
            String key = slot.serviceDate() + "#" + mealType.name();
            if (!seen.add(key)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "同一服务日的同一餐次重复提交：" + slot.serviceDate() + " " + mealType.getLabel());
            }
            DishVersionDO version = dishVersionMapper.selectById(slot.dishVersionId());
            if (version == null) {
                throw new ServiceException(ErrorCode.DISH_NOT_FOUND, "菜品版本不存在：" + slot.dishVersionId());
            }
            if (!DishVersionStatus.ACTIVE.name().equals(version.getStatus())) {
                throw new ServiceException(ErrorCode.MENU_STATUS_INVALID,
                        "只能引用启用中的菜品版本，当前状态：" + version.getStatus());
            }
            MenuMealDO meal = new MenuMealDO();
            meal.setMenuId(id);
            meal.setMenuDayId(day.getId());
            meal.setMealType(mealType.name());
            meal.setDishVersionId(version.getId());
            meal.setDeliveryBatch(DeliveryBatch.forMealType(mealType).name());
            meal.setSortNo(mealType.getSort());
            toInsert.add(meal);
        }

        menuMealMapper.delete(new LambdaQueryWrapper<MenuMealDO>().eq(MenuMealDO::getMenuId, id));
        toInsert.forEach(menuMealMapper::insert);
        recomputeDays(menu, days);
    }

    /**
     * 发布前检查。返回逐项结果与全周标准成本。
     *
     * <p>含 1 项人工确认（厨房设备与产能）与 1 项依赖定价规则的成本率检查，
     * 二者不阻止发布，但会在结果中标记为 MANUAL。
     */
    public MenuCheckResp check(Long id) {
        WeekMenuDO menu = requireMenu(id);
        List<MenuDayDO> days = listDays(id);
        List<MenuMealDO> meals = listMeals(id);
        List<MenuCheckResp.Item> items = new ArrayList<>();

        // 1. 21 个餐次完整
        int expected = days.size() * MealType.values().length;
        Set<String> present = meals.stream()
                .map(m -> m.getMenuDayId() + "#" + m.getMealType())
                .collect(Collectors.toSet());
        int missing = 0;
        for (MenuDayDO day : days) {
            for (MealType type : MealType.values()) {
                if (!present.contains(day.getId() + "#" + type.name())) {
                    missing++;
                }
            }
        }
        items.add(new MenuCheckResp.Item("21 个餐次配置完整",
                missing == 0 && meals.size() == expected ? MenuCheckResp.Status.PASS : MenuCheckResp.Status.FAIL,
                missing == 0 ? "已配置 " + meals.size() + " 个餐次" : "缺少 " + missing + " 个餐次"));

        // 2. 菜品版本与 BOM 完整
        Map<Long, DishVersionDO> versionMap = loadVersions(meals);
        Map<Long, Long> bomCounts = countBom(versionMap.keySet());
        List<String> badVersions = new ArrayList<>();
        for (MenuMealDO meal : meals) {
            DishVersionDO version = versionMap.get(meal.getDishVersionId());
            if (version == null || !DishVersionStatus.ACTIVE.name().equals(version.getStatus())) {
                badVersions.add("餐次 " + meal.getMenuDayId() + "/" + meal.getMealType() + " 版本不可用");
            } else if (bomCounts.getOrDefault(version.getId(), 0L) == 0L) {
                badVersions.add("菜品版本 " + version.getId() + " 缺少 BOM");
            }
        }
        items.add(new MenuCheckResp.Item("菜品版本与菜谱 BOM 完整",
                badVersions.isEmpty() ? MenuCheckResp.Status.PASS : MenuCheckResp.Status.FAIL,
                badVersions.isEmpty() ? "全部菜品版本含 BOM" : String.join("；", limit(badVersions, 3))));

        // 3. 营养与全天重量范围：
        //    低于下限视为菜单不完整，阻止发布；超过上限（高热量/高蛋白/大份量）只提示人工确认，
        //    不阻止发布——否则增肌用户会因为「总热量太高」而无法生成计划。
        List<String> belowMin = new ArrayList<>();
        List<String> aboveMax = new ArrayList<>();
        MealProperties.MenuCheck cfg = properties.getMenuCheck();
        for (MenuDayDO day : days) {
            int weight = day.getTotalWeightG() == null ? 0 : day.getTotalWeightG();
            if (weight < cfg.getDailyWeightMinG()) {
                belowMin.add(day.getServiceDate() + " 全天重量 " + weight + "g");
            } else if (weight > cfg.getDailyWeightMaxG()) {
                aboveMax.add(day.getServiceDate() + " 全天重量 " + weight + "g");
            }
            double protein = day.getTotalProteinG() == null ? 0 : day.getTotalProteinG().doubleValue();
            if (protein < cfg.getStandardMenuProteinMinG()) {
                belowMin.add(day.getServiceDate() + " 蛋白质 " + protein + "g");
            } else if (protein > cfg.getStandardMenuProteinMaxG()) {
                aboveMax.add(day.getServiceDate() + " 蛋白质 " + protein + "g");
            }
            int kcal = day.getTotalKcal() == null ? 0 : day.getTotalKcal();
            if (kcal < cfg.getDailyKcalMin()) {
                belowMin.add(day.getServiceDate() + " 热量 " + kcal + "kcal");
            } else if (kcal > cfg.getDailyKcalMax()) {
                aboveMax.add(day.getServiceDate() + " 热量 " + kcal + "kcal");
            }
        }
        MenuCheckResp.Status nutritionStatus = !belowMin.isEmpty() ? MenuCheckResp.Status.FAIL
                : (!aboveMax.isEmpty() ? MenuCheckResp.Status.MANUAL : MenuCheckResp.Status.PASS);
        String nutritionMessage;
        if (!belowMin.isEmpty()) {
            nutritionMessage = "低于下限：" + String.join("；", limit(belowMin, 3));
        } else if (!aboveMax.isEmpty()) {
            nutritionMessage = "超出常规上限（不阻止发布，请人工确认）：" + String.join("；", limit(aboveMax, 3));
        } else {
            nutritionMessage = "重量 " + cfg.getDailyWeightMinG() + "-" + cfg.getDailyWeightMaxG() + "g，公共标准菜单蛋白质 "
                    + (int) cfg.getStandardMenuProteinMinG() + "-" + (int) cfg.getStandardMenuProteinMaxG()
                    + "g（个人计划按定价版本 80–220g 另行校验）";
        }
        items.add(new MenuCheckResp.Item("营养与全天重量在允许范围", nutritionStatus, nutritionMessage));

        // 4. 过敏原、保存、复热与配送批次完整
        Map<Long, DishDO> dishMap = loadDishes(versionMap.values());
        List<String> missingFields = new ArrayList<>();
        for (MenuMealDO meal : meals) {
            DishVersionDO version = versionMap.get(meal.getDishVersionId());
            if (version == null) {
                continue;
            }
            DishDO dish = dishMap.get(version.getDishId());
            if (dish == null) {
                missingFields.add("餐次 " + meal.getMealType() + " 菜品不存在");
                continue;
            }
            if (isBlank(dish.getStorageMethod())) {
                missingFields.add(dish.getName() + " 缺少保存方式");
            }
            if (isBlank(dish.getReheatMethod())) {
                missingFields.add(dish.getName() + " 缺少复热方式");
            }
            if (isBlank(meal.getDeliveryBatch())) {
                missingFields.add(dish.getName() + " 缺少配送批次");
            }
        }
        items.add(new MenuCheckResp.Item("过敏原、保存、复热与配送批次完整",
                missingFields.isEmpty() ? MenuCheckResp.Status.PASS : MenuCheckResp.Status.FAIL,
                missingFields.isEmpty() ? "四项信息齐全" : String.join("；", limit(missingFields, 3))));

        // 5. 成本与成本率
        BigDecimal standardCost = totalStandardCost(meals, versionMap);
        BigDecimal costRate = menu.getExpectedCostRate();
        MenuCheckResp.Status costStatus;
        String costDetail;
        if (costRate == null) {
            costStatus = MenuCheckResp.Status.MANUAL;
            costDetail = "已算出全周标准食材成本 " + standardCost + " 元；未填写预计成本率，待定价规则接入后自动计算";
        } else if (costRate.doubleValue() > properties.getMenuCheck().getCostRateWarn()) {
            costStatus = MenuCheckResp.Status.FAIL;
            costDetail = "预计成本率 " + percent(costRate) + " 超过预警线 "
                    + percent(BigDecimal.valueOf(properties.getMenuCheck().getCostRateWarn()));
        } else {
            costStatus = MenuCheckResp.Status.PASS;
            costDetail = "预计成本率 " + percent(costRate) + "，未超预警线 "
                    + percent(BigDecimal.valueOf(properties.getMenuCheck().getCostRateWarn()));
        }
        items.add(new MenuCheckResp.Item("预计食材成本率未超预警线", costStatus, costDetail));

        // 6. 厨房产能（人工）
        items.add(new MenuCheckResp.Item("厨房设备与产能可承接", MenuCheckResp.Status.MANUAL,
                "需厨房负责人确认设备、工序与预测订单量"));

        boolean passed = items.stream().noneMatch(i -> i.status() == MenuCheckResp.Status.FAIL);
        return new MenuCheckResp(passed, items, standardCost, costRate);
    }

    /** 发布：检查通过后冻结版本，记录发布时间与标准成本。 */
    @Transactional
    public void publish(Long id) {
        WeekMenuDO menu = requireDraft(id);
        MenuCheckResp result = check(id);
        if (!result.passed()) {
            String failed = result.items().stream()
                    .filter(i -> i.status() == MenuCheckResp.Status.FAIL)
                    .map(MenuCheckResp.Item::name)
                    .collect(Collectors.joining("；"));
            throw new ServiceException(ErrorCode.MENU_PUBLISH_CHECK_FAILED, "发布前检查未通过：" + failed);
        }
        menu.setStatus(MenuStatus.PUBLISHED.name());
        menu.setPublishTime(LocalDateTime.now());
        menu.setStandardCost(result.standardCost());
        weekMenuMapper.updateById(menu);
        auditLogService.record("MENU_PUBLISH", "WEEK_MENU", id,
                "发布 " + menu.getThemeName() + " " + menu.getVersion());
    }

    /** 复制已有菜单到新的开始日（常见用法：复制上周）。 */
    @Transactional
    public Long copy(Long sourceId, WeekMenuReqs.Copy req) {
        WeekMenuDO source = requireMenu(sourceId);
        requireMonday(req.startDate(), "新开始日期必须是周一");
        if (weekMenuMapper.selectCount(new LambdaQueryWrapper<WeekMenuDO>()
                .eq(WeekMenuDO::getStartDate, req.startDate())
                .eq(WeekMenuDO::getVersion, req.version())) > 0) {
            throw new ServiceException(ErrorCode.MENU_VERSION_DUPLICATED);
        }
        WeekMenuDO target = new WeekMenuDO();
        target.setThemeName(source.getThemeName());
        target.setRegion(source.getRegion());
        target.setStartDate(req.startDate());
        target.setEndDate(req.startDate().plusDays(6));
        target.setVersion(req.version());
        target.setStatus(MenuStatus.DRAFT.name());
        target.setRemark("复制自菜单 " + sourceId);
        weekMenuMapper.insert(target);
        createDays(target.getId(), target.getStartDate());

        long shift = ChronoUnit.DAYS.between(source.getStartDate(), req.startDate());
        Map<LocalDate, MenuDayDO> targetDays = listDays(target.getId()).stream()
                .collect(Collectors.toMap(d -> d.getServiceDate().minusDays(shift), d -> d));

        for (MenuMealDO meal : listMeals(sourceId)) {
            MenuDayDO sourceDay = menuDayMapper.selectById(meal.getMenuDayId());
            if (sourceDay == null) {
                continue;
            }
            MenuDayDO targetDay = targetDays.get(sourceDay.getServiceDate());
            if (targetDay == null) {
                continue;
            }
            MenuMealDO copied = new MenuMealDO();
            copied.setMenuId(target.getId());
            copied.setMenuDayId(targetDay.getId());
            copied.setMealType(meal.getMealType());
            copied.setDishVersionId(meal.getDishVersionId());
            copied.setDeliveryBatch(meal.getDeliveryBatch());
            copied.setSortNo(meal.getSortNo());
            menuMealMapper.insert(copied);
        }
        recomputeDays(target, listDays(target.getId()));
        return target.getId();
    }

    // ==================== 读侧 ====================

    public PageResult<WeekMenuDO> page(String status, int pageNo, int pageSize) {
        LambdaQueryWrapper<WeekMenuDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(WeekMenuDO::getStatus, status);
        }
        wrapper.orderByDesc(WeekMenuDO::getStartDate);
        Page<WeekMenuDO> page = weekMenuMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    public WeekMenuDO getHeader(Long id) {
        return requireMenu(id);
    }

    public WeekMenuDetailResp detail(Long id) {
        WeekMenuDO menu = requireMenu(id);
        List<MenuDayDO> days = listDays(id);
        List<MenuMealDO> meals = listMeals(id);
        Map<Long, DishVersionDO> versionMap = loadVersions(meals);
        Map<Long, DishDO> dishMap = loadDishes(versionMap.values());
        Map<Long, List<String>> allergens = allergenResolver.byVersionIds(versionMap.keySet());

        Map<Long, List<MenuMealDO>> mealsByDay = meals.stream()
                .collect(Collectors.groupingBy(MenuMealDO::getMenuDayId));

        List<WeekMenuDetailResp.Day> dayResps = new ArrayList<>();
        for (MenuDayDO day : days) {
            List<WeekMenuDetailResp.Meal> mealResps = new ArrayList<>();
            for (MenuMealDO meal : mealsByDay.getOrDefault(day.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(MenuMealDO::getSortNo))
                    .toList()) {
                DishVersionDO version = versionMap.get(meal.getDishVersionId());
                if (version == null) {
                    continue;
                }
                DishDO dish = dishMap.get(version.getDishId());
                MealType type = MealType.valueOf(meal.getMealType());
                mealResps.add(new WeekMenuDetailResp.Meal(
                        meal.getId(), meal.getMealType(), type.getLabel(), meal.getSortNo(),
                        meal.getDeliveryBatch(), version.getId(), version.getDishId(),
                        dish == null ? "未知菜品" : dish.getName(),
                        dish == null ? "" : dish.getCategory(),
                        dish == null ? null : dish.getDescription(),
                        dish == null ? null : dish.getImageUrl(),
                        dish == null ? null : dish.getStorageMethod(),
                        dish == null ? null : dish.getReheatMethod(),
                        version.getNetWeightG(), version.getKcal(), version.getProteinG(),
                        points(version.getProteinG()), version.getCost(),
                        allergens.getOrDefault(version.getId(), List.of()),
                        Integer.valueOf(version.getStatus().equals(DishVersionStatus.ACTIVE.name()) ? 0 : 1)));
            }
            dayResps.add(new WeekMenuDetailResp.Day(day.getServiceDate(), day.getDayOfWeek(),
                    day.getTotalWeightG(), day.getTotalProteinG(), day.getTotalKcal(), day.getTotalPoints(),
                    mealResps));
        }
        return new WeekMenuDetailResp(menu.getId(), menu.getThemeName(), menu.getRegion(),
                menu.getStartDate(), menu.getEndDate(), menu.getVersion(), menu.getStatus(),
                menu.getPublishTime(), menu.getStandardCost(), menu.getExpectedCostRate(), menu.getRemark(),
                meals.size(), dayResps);
    }

    /**
     * 小程序公共菜单：只返回已发布版本的内容。
     * 未发布时不泄露草稿，仅返回预计发布时间（手册 §3「未发布时显示预计发布时间」）。
     * 未注册用户不展示价格与个人克重。
     */
    public PublicWeekResp publicWeek(LocalDate anyDateInWeek) {
        // LocalDate.with(DAY_OF_WEEK) 按 ISO 周（周一为首日）对齐，周日会回到本周周一
        LocalDate weekStart = anyDateInWeek.with(DayOfWeek.MONDAY);
        LocalDateTime expectedPublishTime = weekStart.minusDays(PUBLISH_AHEAD_DAYS).atTime(PUBLISH_TIME);
        WeekMenuDO menu = weekMenuMapper.selectOne(new LambdaQueryWrapper<WeekMenuDO>()
                .eq(WeekMenuDO::getStartDate, weekStart)
                .eq(WeekMenuDO::getStatus, MenuStatus.PUBLISHED.name())
                .orderByDesc(WeekMenuDO::getPublishTime)
                .last("LIMIT 1"));
        if (menu == null) {
            WeekMenuDO anyDraft = weekMenuMapper.selectOne(new LambdaQueryWrapper<WeekMenuDO>()
                    .eq(WeekMenuDO::getStartDate, weekStart)
                    .eq(WeekMenuDO::getStatus, MenuStatus.DRAFT.name())
                    .last("LIMIT 1"));
            String theme = anyDraft == null ? "本周菜单" : anyDraft.getThemeName();
            String region = anyDraft == null ? "" : anyDraft.getRegion();
            return new PublicWeekResp(null, theme, region, weekStart, weekStart.plusDays(6),
                    false, expectedPublishTime, List.of());
        }        List<MenuDayDO> days = listDays(menu.getId());
        List<MenuMealDO> meals = listMeals(menu.getId());
        Map<Long, DishVersionDO> versionMap = loadVersions(meals);
        Map<Long, DishDO> dishMap = loadDishes(versionMap.values());
        Map<Long, List<String>> allergens = allergenResolver.byVersionIds(versionMap.keySet());
        Map<Long, List<MenuMealDO>> mealsByDay = meals.stream()
                .collect(Collectors.groupingBy(MenuMealDO::getMenuDayId));

        List<PublicWeekResp.Day> dayResps = new ArrayList<>();
        for (MenuDayDO day : days) {
            List<PublicWeekResp.Meal> mealResps = new ArrayList<>();
            for (MenuMealDO meal : mealsByDay.getOrDefault(day.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(MenuMealDO::getSortNo))
                    .toList()) {
                DishVersionDO version = versionMap.get(meal.getDishVersionId());
                if (version == null) {
                    continue;
                }
                DishDO dish = dishMap.get(version.getDishId());
                MealType type = MealType.valueOf(meal.getMealType());
                mealResps.add(new PublicWeekResp.Meal(
                        meal.getMealType(), type.getLabel(), meal.getDeliveryBatch(),
                        dish == null ? "未知菜品" : dish.getName(),
                        dish == null ? "" : dish.getCategory(),
                        dish == null ? null : dish.getDescription(),
                        dish == null ? null : dish.getImageUrl(),
                        version.getNetWeightG(), version.getKcal(), version.getProteinG(),
                        points(version.getProteinG()),
                        allergens.getOrDefault(version.getId(), List.of()),
                        dish == null ? null : dish.getStorageMethod(),
                        dish == null ? null : dish.getReheatMethod()));
            }
            dayResps.add(new PublicWeekResp.Day(day.getServiceDate(), day.getDayOfWeek(),
                    day.getTotalWeightG(), day.getTotalProteinG(), day.getTotalKcal(), day.getTotalPoints(),
                    mealResps));
        }
        return new PublicWeekResp(menu.getId(), menu.getThemeName(), menu.getRegion(),
                menu.getStartDate(), menu.getEndDate(), true, null, dayResps);
    }

    /** 按周查询已发布菜单；没有则返回 null。 */
    public WeekMenuDO findPublishedByWeekStart(LocalDate weekStart) {
        return weekMenuMapper.selectOne(new LambdaQueryWrapper<WeekMenuDO>()
                .eq(WeekMenuDO::getStartDate, weekStart)
                .eq(WeekMenuDO::getStatus, MenuStatus.PUBLISHED.name())
                .orderByDesc(WeekMenuDO::getPublishTime)
                .last("LIMIT 1"));
    }

    /** 已发布周列表，供「本周 / 下周」切换。 */
    public List<WeekMenuDO> listPublished() {
        return weekMenuMapper.selectList(new LambdaQueryWrapper<WeekMenuDO>()
                .eq(WeekMenuDO::getStatus, MenuStatus.PUBLISHED.name())
                .orderByDesc(WeekMenuDO::getStartDate)
                .last("LIMIT 12"));
    }

    // ==================== 内部 ====================

    private void createDays(Long menuId, LocalDate startDate) {
        for (int i = 0; i < 7; i++) {
            MenuDayDO day = new MenuDayDO();
            day.setMenuId(menuId);
            day.setServiceDate(startDate.plusDays(i));
            day.setDayOfWeek(i + 1);
            day.setTotalWeightG(0);
            day.setTotalProteinG(BigDecimal.ZERO);
            day.setTotalKcal(0);
            day.setTotalPoints(BigDecimal.ZERO);
            menuDayMapper.insert(day);
        }
    }

    private void recomputeDays(WeekMenuDO menu, List<MenuDayDO> days) {
        List<MenuMealDO> meals = listMeals(menu.getId());
        Map<Long, DishVersionDO> versionMap = loadVersions(meals);
        Map<Long, List<MenuMealDO>> byDay = meals.stream()
                .collect(Collectors.groupingBy(MenuMealDO::getMenuDayId));
        for (MenuDayDO day : days) {
            int weight = 0;
            int kcal = 0;
            BigDecimal protein = BigDecimal.ZERO;
            for (MenuMealDO meal : byDay.getOrDefault(day.getId(), List.of())) {
                DishVersionDO version = versionMap.get(meal.getDishVersionId());
                if (version == null) {
                    continue;
                }
                weight += version.getNetWeightG() == null ? 0 : version.getNetWeightG();
                kcal += version.getKcal() == null ? 0 : version.getKcal();
                protein = protein.add(version.getProteinG() == null ? BigDecimal.ZERO : version.getProteinG());
            }
            day.setTotalWeightG(weight);
            day.setTotalKcal(kcal);
            day.setTotalProteinG(protein.setScale(2, RoundingMode.HALF_UP));
            day.setTotalPoints(points(protein));
            menuDayMapper.updateById(day);
        }
    }

    private WeekMenuDO requireMenu(Long id) {
        WeekMenuDO menu = weekMenuMapper.selectById(id);
        if (menu == null) {
            throw new ServiceException(ErrorCode.MENU_NOT_FOUND);
        }
        return menu;
    }

    private WeekMenuDO requireDraft(Long id) {
        WeekMenuDO menu = requireMenu(id);
        if (!MenuStatus.valueOf(menu.getStatus()).isDraft()) {
            throw new ServiceException(ErrorCode.MENU_ALREADY_PUBLISHED,
                    "菜单已" + (MenuStatus.PUBLISHED.name().equals(menu.getStatus()) ? "发布" : "归档")
                            + "，不能原地修改；请新建版本");
        }
        return menu;
    }

    public List<MenuDayDO> listDays(Long menuId) {
        return menuDayMapper.selectList(new LambdaQueryWrapper<MenuDayDO>()
                .eq(MenuDayDO::getMenuId, menuId)
                .orderByAsc(MenuDayDO::getServiceDate));
    }

    public List<MenuMealDO> listMeals(Long menuId) {
        return menuMealMapper.selectList(new LambdaQueryWrapper<MenuMealDO>()
                .eq(MenuMealDO::getMenuId, menuId));
    }

    private Map<Long, DishVersionDO> loadVersions(List<MenuMealDO> meals) {
        Set<Long> ids = meals.stream().map(MenuMealDO::getDishVersionId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return dishVersionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(DishVersionDO::getId, v -> v));
    }

    private Map<Long, DishDO> loadDishes(java.util.Collection<DishVersionDO> versions) {
        Set<Long> dishIds = versions.stream().map(DishVersionDO::getDishId).collect(Collectors.toSet());
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return dishMapper.selectBatchIds(dishIds).stream().collect(Collectors.toMap(DishDO::getId, d -> d));
    }

    private Map<Long, Long> countBom(Set<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return recipeBomMapper.selectList(new LambdaQueryWrapper<RecipeBomDO>()
                        .in(RecipeBomDO::getDishVersionId, versionIds))
                .stream()
                .collect(Collectors.groupingBy(RecipeBomDO::getDishVersionId, Collectors.counting()));
    }

    private BigDecimal totalStandardCost(List<MenuMealDO> meals, Map<Long, DishVersionDO> versionMap) {
        BigDecimal total = BigDecimal.ZERO;
        for (MenuMealDO meal : meals) {
            DishVersionDO version = versionMap.get(meal.getDishVersionId());
            if (version != null && version.getCost() != null) {
                total = total.add(version.getCost());
            }
        }
        return total.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal points(BigDecimal proteinG) {
        if (proteinG == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return proteinG.divide(POINTS_PER_GRAM_PROTEIN, 2, RoundingMode.HALF_UP);
    }

    private List<String> splitAllergens(String raw) {
        return Arrays.stream(raw.split("[,，/、]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void requireMonday(LocalDate date, String message) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private List<String> limit(List<String> values, int max) {
        if (values.size() <= max) {
            return values;
        }
        List<String> result = new ArrayList<>(values.subList(0, max));
        result.add("等 " + values.size() + " 项");
        return result;
    }
}
