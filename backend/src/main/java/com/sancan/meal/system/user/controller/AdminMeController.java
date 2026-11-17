package com.sancan.meal.system.user.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.LoginUser;
import com.sancan.meal.common.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台「当前登录人」信息，用于前端菜单与权限初始化。 */
@RestController
@RequestMapping("/admin/me")
public class AdminMeController {

    public record AdminMeResp(Long id, String nickname, java.util.Set<String> roles) {
    }

    @GetMapping
    public CommonResult<AdminMeResp> me() {
        LoginUser user = UserContext.require();
        return CommonResult.ok(new AdminMeResp(user.getId(), user.getNickname(), user.getRoles()));
    }
}
