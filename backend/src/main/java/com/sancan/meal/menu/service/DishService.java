package com.sancan.meal.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.controller.dto.DishDetailResp;
import com.sancan.meal.menu.controller.dto.DishVersionSaveReq;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import com.sancan.meal.menu.enums.DishVersionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 菜品、菜品版本与菜谱 BOM（手册 §6、§10）。 */
@Service
public class DishService {

    private final DishMapper dishMapper;
    private final DishVersionMapper dishVersionMapper;
    private final RecipeBomMapper recipeBomMapper;
    private final IngredientMapper ingredientMapper;

    public DishService(DishMapper dishMapper,
                       DishVersionMapper dishVersionMapper,
                       RecipeBomMapper recipeBomMapper,
                       IngredientMapper ingredientMapper) {
        this.dishMapper = dishMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.recipeBomMapper = recipeBomMapper;
        this.ingredientMapper = ingredientMapper;
    }

    public PageResult<DishDO> page(String keyword, String category, int pageNo, int pageSize) {
        LambdaQueryWrapper<DishDO> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(DishDO::getName, keyword.trim());
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(DishDO::getCategory, category);
        }
        wrapper.orderByDesc(DishDO::getId);
        Page<DishDO> page = dishMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /** 供后台下拉选择的菜品版本列表。 */
    public record VersionOption(Long id, Long dishId, String dishName, String category, Integer version,
                                Integer netWeightG, BigDecimal proteinG, BigDecimal cost, String status) {
    }

    public List<VersionOption> listVersionOptions() {
        List<DishVersionDO> versions = dishVersionMapper.selectList(new LambdaQueryWrapper<DishVersionDO>()
                .orderByAsc(DishVersionDO::getDishId)
                .orderByDesc(DishVersionDO::getVersion));
        if (versions.isEmpty()) {
            return List.of();
        }
        Map<Long, DishDO> dishes = dishMapper.selectBatchIds(
                        versions.stream().map(DishVersionDO::getDishId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DishDO::getId, d -> d));
        return versions.stream().map(v -> {
            DishDO dish = dishes.get(v.getDishId());
            return new VersionOption(v.getId(), v.getDishId(),
                    dish == null ? "未知菜品" : dish.getName(),
                    dish == null ? "" : dish.getCategory(),
                    v.getVersion(), v.getNetWeightG(), v.getProteinG(), v.getCost(), v.getStatus());
        }).toList();
    }

    public DishDO getDish(Long id) {
        DishDO dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new ServiceException(ErrorCode.DISH_NOT_FOUND);
        }
        return dish;
    }

    /** 更新菜品主数据；已发布菜单引用的版本营养与成本不受影响（版本冻结）。 */
    public List<DishVersionDO> listVersions(Long dishId) {
        return dishVersionMapper.selectList(new LambdaQueryWrapper<DishVersionDO>()
                .eq(DishVersionDO::getDishId, dishId)
                .orderByDesc(DishVersionDO::getVersion));
    }

    public DishVersionDO getVersion(Long versionId) {
        DishVersionDO version = dishVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ServiceException(ErrorCode.DISH_NOT_FOUND, "菜品版本不存在：" + versionId);
        }
        return version;
    }

    public DishDetailResp detail(Long id) {
        DishDO dish = getDish(id);
        List<DishVersionDO> versions = listVersions(id);
        Map<Long, List<RecipeBomDO>> bomByVersion = versions.isEmpty()
                ? Map.of()
                : recipeBomMapper.selectList(new LambdaQueryWrapper<RecipeBomDO>()
                        .in(RecipeBomDO::getDishVersionId, versions.stream().map(DishVersionDO::getId).toList())
                        .orderByAsc(RecipeBomDO::getSortNo))
                .stream()
                .collect(Collectors.groupingBy(RecipeBomDO::getDishVersionId, LinkedHashMap::new, Collectors.toList()));

        Set<Long> ingredientIds = bomByVersion.values().stream()
                .flatMap(List::stream)
                .map(RecipeBomDO::getIngredientId)
                .collect(Collectors.toSet());
        Map<Long, IngredientDO> ingredientMap = ingredientIds.isEmpty()
                ? Map.of()
                : ingredientMapper.selectBatchIds(ingredientIds).stream()
                        .collect(Collectors.toMap(IngredientDO::getId, i -> i));

        List<DishDetailResp.Version> versionResps = new ArrayList<>();
        for (DishVersionDO version : versions) {
            List<RecipeBomDO> lines = bomByVersion.getOrDefault(version.getId(), List.of());
            List<DishDetailResp.BomLine> bomResps = new ArrayList<>();
            for (RecipeBomDO line : lines) {
                IngredientDO ingredient = ingredientMap.get(line.getIngredientId());
                BigDecimal lineCost = ingredient == null ? BigDecimal.ZERO
                        : IngredientCostCalculator.cost(ingredient, line.getNetWeightG());
                bomResps.add(new DishDetailResp.BomLine(
                        line.getIngredientId(),
                        ingredient == null ? "已删除食材" : ingredient.getName(),
                        ingredient == null ? "" : ingredient.getUsageUnit(),
                        line.getNetWeightG(),
                        lineCost,
                        line.getNote(),
                        line.getComponentType()));
            }
            versionResps.add(new DishDetailResp.Version(
                    version.getId(), version.getVersion(), version.getNetWeightG(), version.getKcal(),
                    version.getProteinG(), version.getCarbG(), version.getFatG(), version.getCost(),
                    version.getStatus(), version.getRemark(), bomResps));
        }

        List<String> allergens = collectAllergens(dish, ingredientMap.values());
        return new DishDetailResp(dish.getId(), dish.getName(), dish.getCategory(), dish.getDescription(),
                dish.getImageUrl(), dish.getStorageMethod(), dish.getReheatMethod(), allergens,
                dish.getStatus(), versionResps);
    }

    /** 过敏原 = 额外声明 ∪ 所有版本 BOM 食材的过敏原。 */
    private static final Set<String> COMPONENT_TYPES =
            Set.of("PROTEIN", "SAUCE", "STAPLE", "VEGETABLE", "FAT", "OTHER");

    private String normalizeComponentType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String upper = type.trim().toUpperCase();
        if (!COMPONENT_TYPES.contains(upper)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "组件类型不正确：" + type);
        }
        return upper;
    }

    public List<String> collectAllergens(DishDO dish, java.util.Collection<IngredientDO> ingredients) {
        Set<String> result = new LinkedHashSet<>();
        if (dish.getExtraAllergens() != null && !dish.getExtraAllergens().isBlank()) {
            result.addAll(splitAllergens(dish.getExtraAllergens()));
        }
        for (IngredientDO ingredient : ingredients) {
            if (ingredient.getAllergens() != null && !ingredient.getAllergens().isBlank()) {
                result.addAll(splitAllergens(ingredient.getAllergens()));
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> splitAllergens(String raw) {
        return Arrays.stream(raw.split("[,，/、]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Transactional
    public Long create(DishVersionSaveReq.DishSaveReq req) {
        DishDO exists = dishMapper.selectOne(new LambdaQueryWrapper<DishDO>()
                .eq(DishDO::getName, req.name().trim()));
        if (exists != null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "菜品名称已存在：" + req.name());
        }
        DishDO dish = new DishDO();
        apply(dish, req);
        dishMapper.insert(dish);
        return dish.getId();
    }

    @Transactional
    public void update(Long id, DishVersionSaveReq.DishSaveReq req) {
        DishDO dish = getDish(id);
        apply(dish, req);
        dishMapper.updateById(dish);
    }

    /**
     * 新增菜品版本：计算成本、归档旧版本、写入 BOM。
     * 版本一旦被菜单引用即冻结，修改只能新增版本。
     */
    @Transactional
    public Long createVersion(Long dishId, DishVersionSaveReq req) {
        DishDO dish = getDish(dishId);
        if (req.netWeightG() == null || req.netWeightG() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "成品净重必须大于 0");
        }
        List<DishVersionSaveReq.BomLineReq> lines = req.bom();
        Set<Long> ingredientIds = new LinkedHashSet<>();
        Set<String> lineKeys = new HashSet<>();
        for (DishVersionSaveReq.BomLineReq line : lines) {
            String componentType = normalizeComponentType(line.componentType());
            // 同一食材只能出现一次；指定了不同组件类型时允许各出现一次
            if (!lineKeys.add(line.ingredientId() + "#" + (componentType == null ? "" : componentType))) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "BOM 中食材重复：" + line.ingredientId());
            }
            ingredientIds.add(line.ingredientId());
            if (line.netWeightG() == null || line.netWeightG().signum() <= 0) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "BOM 可食生重必须大于 0");
            }
        }
        Map<Long, IngredientDO> ingredientMap = new HashMap<>();
        for (IngredientDO ingredient : ingredientMapper.selectBatchIds(ingredientIds)) {
            ingredientMap.put(ingredient.getId(), ingredient);
        }
        if (ingredientMap.size() != ingredientIds.size()) {
            throw new ServiceException(ErrorCode.INGREDIENT_NOT_FOUND, "BOM 中存在不存在的食材");
        }

        int nextVersion = listVersions(dishId).stream()
                .mapToInt(DishVersionDO::getVersion)
                .max()
                .orElse(0) + 1;

        // 旧 ACTIVE 版本归档，保证同一菜品只有一个可用版本
        dishVersionMapper.update(null, new LambdaUpdateWrapper<DishVersionDO>()
                .eq(DishVersionDO::getDishId, dishId)
                .eq(DishVersionDO::getStatus, DishVersionStatus.ACTIVE.name())
                .set(DishVersionDO::getStatus, DishVersionStatus.ARCHIVED.name()));

        DishVersionDO version = new DishVersionDO();
        version.setDishId(dishId);
        version.setVersion(nextVersion);
        version.setNetWeightG(req.netWeightG());
        version.setKcal(req.kcal() == null ? 0 : req.kcal());
        version.setProteinG(req.proteinG() == null ? BigDecimal.ZERO : req.proteinG());
        version.setCarbG(req.carbG() == null ? BigDecimal.ZERO : req.carbG());
        version.setFatG(req.fatG() == null ? BigDecimal.ZERO : req.fatG());
        version.setRemark(req.remark());
        version.setStatus(DishVersionStatus.ACTIVE.name());

        List<RecipeBomDO> bomDOs = new ArrayList<>();
        int sort = 0;
        for (DishVersionSaveReq.BomLineReq line : lines) {
            RecipeBomDO bom = new RecipeBomDO();
            bom.setIngredientId(line.ingredientId());
            bom.setNetWeightG(line.netWeightG());
            bom.setNote(line.note());
            bom.setComponentType(normalizeComponentType(line.componentType()));
            bom.setCookedWeightG(line.cookedWeightG());
            bom.setSortNo(sort++);
            bomDOs.add(bom);
        }
        version.setCost(IngredientCostCalculator.dishCost(bomDOs, ingredientMap));
        dishVersionMapper.insert(version);

        for (RecipeBomDO bom : bomDOs) {
            bom.setDishVersionId(version.getId());
            recipeBomMapper.insert(bom);
        }
        // 菜品必须处于启用状态才能被菜单引用
        if (dish.getStatus() != null && dish.getStatus() != 0) {
            dish.setStatus(0);
            dishMapper.updateById(dish);
        }
        return version.getId();
    }

    private void apply(DishDO dish, DishVersionSaveReq.DishSaveReq req) {
        dish.setName(req.name().trim());
        dish.setCategory(req.category());
        dish.setDescription(req.description());
        dish.setImageUrl(req.imageUrl());
        dish.setStorageMethod(req.storageMethod());
        dish.setReheatMethod(req.reheatMethod());
        dish.setExtraAllergens(req.extraAllergens());
        dish.setStatus(req.status() == null ? 0 : req.status());
    }
}
