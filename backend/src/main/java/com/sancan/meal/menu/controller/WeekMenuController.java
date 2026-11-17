package com.sancan.meal.menu.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.menu.controller.dto.MenuCheckResp;
import com.sancan.meal.menu.controller.dto.WeekMenuDetailResp;
import com.sancan.meal.menu.controller.dto.WeekMenuReqs;
import com.sancan.meal.menu.dal.dataobject.WeekMenuDO;
import com.sancan.meal.menu.service.WeekMenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台每周菜单管理（手册 §6）。 */
@RestController
@RequestMapping("/admin/meal/menus")
public class WeekMenuController {

    private final WeekMenuService weekMenuService;

    public WeekMenuController(WeekMenuService weekMenuService) {
        this.weekMenuService = weekMenuService;
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping
    public CommonResult<PageResult<WeekMenuDO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.ok(weekMenuService.page(status, pageNo, pageSize));
    }

    @RequirePermission(Permission.MENU_WRITE)
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody WeekMenuReqs.Create req) {
        return CommonResult.ok(weekMenuService.create(req));
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/{id}")
    public CommonResult<WeekMenuDetailResp> detail(@PathVariable Long id) {
        return CommonResult.ok(weekMenuService.detail(id));
    }

    @RequirePermission(Permission.MENU_WRITE)
    @PutMapping("/{id}")
    public CommonResult<Boolean> updateMeta(@PathVariable Long id,
                                            @Valid @RequestBody WeekMenuReqs.UpdateMeta req) {
        weekMenuService.updateMeta(id, req);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.MENU_WRITE)
    @PutMapping("/{id}/meals")
    public CommonResult<Boolean> upsertMeals(@PathVariable Long id,
                                             @Valid @RequestBody WeekMenuReqs.UpsertMeals req) {
        weekMenuService.upsertMeals(id, req);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/{id}/check")
    public CommonResult<MenuCheckResp> check(@PathVariable Long id) {
        return CommonResult.ok(weekMenuService.check(id));
    }

    @RequirePermission(Permission.MENU_WRITE)
    @PostMapping("/{id}/publish")
    public CommonResult<Boolean> publish(@PathVariable Long id) {
        weekMenuService.publish(id);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.MENU_WRITE)
    @PostMapping("/{id}/copy")
    public CommonResult<Long> copy(@PathVariable Long id,
                                   @Valid @RequestBody WeekMenuReqs.Copy req) {
        return CommonResult.ok(weekMenuService.copy(id, req));
    }
}
