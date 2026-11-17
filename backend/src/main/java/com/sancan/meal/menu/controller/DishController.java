package com.sancan.meal.menu.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.menu.controller.dto.DishDetailResp;
import com.sancan.meal.menu.controller.dto.DishVersionSaveReq;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.service.DishService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/meal/dishes")
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping
    public CommonResult<PageResult<DishDO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.ok(dishService.page(keyword, category, pageNo, pageSize));
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/versions/options")
    public CommonResult<List<DishService.VersionOption>> versionOptions() {
        return CommonResult.ok(dishService.listVersionOptions());
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/{id}")
    public CommonResult<DishDetailResp> detail(@PathVariable Long id) {
        return CommonResult.ok(dishService.detail(id));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody DishVersionSaveReq.DishSaveReq req) {
        return CommonResult.ok(dishService.create(req));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id,
                                        @Valid @RequestBody DishVersionSaveReq.DishSaveReq req) {
        dishService.update(id, req);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/{id}/versions")
    public CommonResult<List<DishVersionDO>> versions(@PathVariable Long id) {
        dishService.getDish(id);
        return CommonResult.ok(dishService.listVersions(id));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping("/{id}/versions")
    public CommonResult<Long> createVersion(@PathVariable Long id,
                                            @Valid @RequestBody DishVersionSaveReq req) {
        return CommonResult.ok(dishService.createVersion(id, req));
    }
}
