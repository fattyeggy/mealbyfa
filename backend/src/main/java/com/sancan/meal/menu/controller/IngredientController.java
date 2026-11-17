package com.sancan.meal.menu.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.menu.controller.dto.IngredientSaveReq;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.service.IngredientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/meal/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping
    public CommonResult<PageResult<IngredientDO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.ok(ingredientService.page(keyword, category, pageNo, pageSize));
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/{id}")
    public CommonResult<IngredientDO> get(@PathVariable Long id) {
        return CommonResult.ok(ingredientService.get(id));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody IngredientSaveReq req) {
        return CommonResult.ok(ingredientService.create(req));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody IngredientSaveReq req) {
        ingredientService.update(id, req);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.DISH_WRITE)
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        ingredientService.delete(id);
        return CommonResult.ok(true);
    }
}
