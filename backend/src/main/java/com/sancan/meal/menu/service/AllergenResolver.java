package com.sancan.meal.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 过敏原解析：菜品过敏原 = BOM 食材过敏原 ∪ 菜品额外声明。
 *
 * <p>周菜单展示、个人计划、下单校验统一走这里，避免各处口径不一致。
 */
@Service
public class AllergenResolver {

    private final RecipeBomMapper recipeBomMapper;
    private final IngredientMapper ingredientMapper;
    private final DishVersionMapper dishVersionMapper;
    private final DishMapper dishMapper;

    public AllergenResolver(RecipeBomMapper recipeBomMapper,
                            IngredientMapper ingredientMapper,
                            DishVersionMapper dishVersionMapper,
                            DishMapper dishMapper) {
        this.recipeBomMapper = recipeBomMapper;
        this.ingredientMapper = ingredientMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.dishMapper = dishMapper;
    }

    /** 批量解析菜品版本的过敏原。 */
    public Map<Long, List<String>> byVersionIds(Collection<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new LinkedHashSet<>(versionIds);
        List<DishVersionDO> versions = dishVersionMapper.selectBatchIds(ids);
        if (versions.isEmpty()) {
            return Map.of();
        }
        Map<Long, DishVersionDO> versionMap = versions.stream()
                .collect(Collectors.toMap(DishVersionDO::getId, v -> v));
        Set<Long> dishIds = versions.stream().map(DishVersionDO::getDishId).collect(Collectors.toSet());
        Map<Long, DishDO> dishMap = dishMapper.selectBatchIds(dishIds).stream()
                .collect(Collectors.toMap(DishDO::getId, d -> d));

        List<RecipeBomDO> boms = recipeBomMapper.selectList(new LambdaQueryWrapper<RecipeBomDO>()
                .in(RecipeBomDO::getDishVersionId, ids));
        Set<Long> ingredientIds = boms.stream().map(RecipeBomDO::getIngredientId).collect(Collectors.toSet());
        Map<Long, IngredientDO> ingredientMap = ingredientIds.isEmpty() ? Map.of()
                : ingredientMapper.selectBatchIds(ingredientIds).stream()
                        .collect(Collectors.toMap(IngredientDO::getId, i -> i));

        Map<Long, List<RecipeBomDO>> bomByVersion = boms.stream()
                .collect(Collectors.groupingBy(RecipeBomDO::getDishVersionId,
                        LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<String>> result = new HashMap<>();
        for (Long versionId : ids) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (RecipeBomDO bom : bomByVersion.getOrDefault(versionId, List.of())) {
                IngredientDO ingredient = ingredientMap.get(bom.getIngredientId());
                if (ingredient != null && ingredient.getAllergens() != null) {
                    set.addAll(split(ingredient.getAllergens()));
                }
            }
            DishVersionDO version = versionMap.get(versionId);
            DishDO dish = version == null ? null : dishMap.get(version.getDishId());
            if (dish != null && dish.getExtraAllergens() != null) {
                set.addAll(split(dish.getExtraAllergens()));
            }
            result.put(versionId, new ArrayList<>(set));
        }
        return result;
    }

    public List<String> forVersion(Long versionId) {
        return byVersionIds(List.of(versionId)).getOrDefault(versionId, List.of());
    }

    public static List<String> split(String raw) {
        return Arrays.stream(raw.split("[,，/、]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 用户过敏原与菜品过敏原的交集。 */
    public static List<String> conflicts(String userAllergies, List<String> dishAllergens) {
        if (userAllergies == null || userAllergies.isBlank() || dishAllergens == null || dishAllergens.isEmpty()) {
            return List.of();
        }
        Set<String> userSet = new LinkedHashSet<>(split(userAllergies));
        return dishAllergens.stream().filter(userSet::contains).toList();
    }
}
