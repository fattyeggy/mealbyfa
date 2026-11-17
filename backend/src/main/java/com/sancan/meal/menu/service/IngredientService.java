package com.sancan.meal.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.controller.dto.IngredientSaveReq;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.dataobject.RecipeBomDO;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.dal.mapper.RecipeBomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 食材主数据服务（手册 §10「必须新增的数据」）。 */
@Service
public class IngredientService {

    private final IngredientMapper ingredientMapper;
    private final RecipeBomMapper recipeBomMapper;

    public IngredientService(IngredientMapper ingredientMapper, RecipeBomMapper recipeBomMapper) {
        this.ingredientMapper = ingredientMapper;
        this.recipeBomMapper = recipeBomMapper;
    }

    public PageResult<IngredientDO> page(String keyword, String category, int pageNo, int pageSize) {
        LambdaQueryWrapper<IngredientDO> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(IngredientDO::getName, keyword.trim());
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(IngredientDO::getCategory, category);
        }
        wrapper.orderByAsc(IngredientDO::getName);
        Page<IngredientDO> page = ingredientMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    public IngredientDO get(Long id) {
        IngredientDO ingredient = ingredientMapper.selectById(id);
        if (ingredient == null) {
            throw new ServiceException(ErrorCode.INGREDIENT_NOT_FOUND);
        }
        return ingredient;
    }

    @Transactional
    public Long create(IngredientSaveReq req) {
        IngredientDO exists = ingredientMapper.selectOne(new LambdaQueryWrapper<IngredientDO>()
                .eq(IngredientDO::getName, req.name().trim()));
        if (exists != null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "食材名称已存在：" + req.name());
        }
        IngredientDO ingredient = new IngredientDO();
        apply(ingredient, req);
        ingredientMapper.insert(ingredient);
        return ingredient.getId();
    }

    @Transactional
    public void update(Long id, IngredientSaveReq req) {
        IngredientDO ingredient = get(id);
        IngredientDO sameName = ingredientMapper.selectOne(new LambdaQueryWrapper<IngredientDO>()
                .eq(IngredientDO::getName, req.name().trim())
                .ne(IngredientDO::getId, id));
        if (sameName != null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "食材名称已存在：" + req.name());
        }
        apply(ingredient, req);
        ingredientMapper.updateById(ingredient);
        // 价格与可食率变化后，引用该食材的菜品成本需要重算——由菜品版本重新激活时完成
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        Long bomRefs = recipeBomMapper.selectCount(new LambdaQueryWrapper<RecipeBomDO>()
                .eq(RecipeBomDO::getIngredientId, id));
        if (bomRefs != null && bomRefs > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "该食材已被 " + bomRefs + " 条菜谱 BOM 引用，不能删除，请先停用");
        }
        ingredientMapper.deleteById(id);
    }

    private void apply(IngredientDO ingredient, IngredientSaveReq req) {
        ingredient.setName(req.name().trim());
        ingredient.setCategory(req.category());
        ingredient.setPurchaseUnit(req.purchaseUnit());
        ingredient.setUsageUnit(req.usageUnit());
        ingredient.setConversionRate(req.conversionRate());
        ingredient.setEdibleRate(req.edibleRate());
        ingredient.setCookingLossRate(req.cookingLossRate() == null ? BigDecimal.ZERO : req.cookingLossRate());
        ingredient.setShelfLifeDays(req.shelfLifeDays());
        ingredient.setAllergens(req.allergens());
        ingredient.setMovingAvgPrice(req.movingAvgPrice());
        ingredient.setComponentType(req.componentType() == null ? "OTHER" : req.componentType().toUpperCase());
        ingredient.setKcalPer100g(req.kcalPer100g());
        ingredient.setProteinPer100g(req.proteinPer100g());
        ingredient.setCarbPer100g(req.carbPer100g());
        ingredient.setFatPer100g(req.fatPer100g());
        ingredient.setStatus(req.status() == null ? 0 : req.status());
        // 提前校验单位可换算，避免入库后才发现成本算不出来
        IngredientCostCalculator.gramFactor(ingredient.getUsageUnit());
    }
}
