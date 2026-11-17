package com.sancan.meal.menu.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.menu.controller.dto.PublicWeekResp;
import com.sancan.meal.menu.dal.dataobject.WeekMenuDO;
import com.sancan.meal.menu.service.WeekMenuService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 小程序公共菜单（未注册用户可见）。已登录用户另由个人计划接口返回个人份量。
 * 手册 §3：未注册用户看到公共菜单，不显示个人克重和价格。
 */
@RestController
@RequestMapping("/app/menu")
public class AppMenuController {

    private final WeekMenuService weekMenuService;

    public AppMenuController(WeekMenuService weekMenuService) {
        this.weekMenuService = weekMenuService;
    }

    @GetMapping("/week")
    public CommonResult<PublicWeekResp> week(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CommonResult.ok(weekMenuService.publicWeek(date == null ? LocalDate.now() : date));
    }

    @GetMapping("/weeks")
    public CommonResult<List<WeekMenuDO>> weeks() {
        return CommonResult.ok(weekMenuService.listPublished());
    }
}
