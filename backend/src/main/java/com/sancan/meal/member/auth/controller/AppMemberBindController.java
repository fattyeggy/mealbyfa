package com.sancan.meal.member.auth.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.member.user.dal.dataobject.MemberUserDO;
import com.sancan.meal.member.user.dal.mapper.MemberUserMapper;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.dal.mapper.UserProfileMapper;
import com.sancan.meal.member.auth.service.MemberBindingService;
import com.sancan.meal.member.auth.service.WeChatApiClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 微信用户绑定手机号，认领后台按手机号建的档。 */
@RestController
@RequestMapping("/app/auth")
public class AppMemberBindController {

    private final MemberBindingService bindingService;
    private final WeChatApiClient weChatApiClient;
    private final MemberUserMapper memberUserMapper;
    private final UserProfileMapper profileMapper;

    public AppMemberBindController(MemberBindingService bindingService, WeChatApiClient weChatApiClient,
                                   MemberUserMapper memberUserMapper, UserProfileMapper profileMapper) {
        this.bindingService = bindingService;
        this.weChatApiClient = weChatApiClient;
        this.memberUserMapper = memberUserMapper;
        this.profileMapper = profileMapper;
    }

    /** 启动判定：是否已绑定手机号、是否已建档。 */
    @org.springframework.web.bind.annotation.GetMapping("/status")
    public CommonResult<java.util.Map<String, Object>> status() {
        Long userId = UserContext.requireMemberId();
        MemberUserDO member = memberUserMapper.selectById(userId);
        UserProfileDO profile = profileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserProfileDO>()
                        .eq(UserProfileDO::getUserId, userId));
        boolean phoneBound = member != null && member.getMobile() != null && !member.getMobile().isBlank();
        boolean completed = profile != null && profile.getOnboardingStatus() != null
                && profile.getOnboardingStatus() == 1;
        return CommonResult.ok(java.util.Map.of(
                "phoneBound", phoneBound,
                "onboardingCompleted", completed,
                "memberId", userId));
    }

    /** 微信手机号授权：code 来自小程序 getPhoneNumber 按钮。 */
    @PostMapping("/wechat-phone")
    public CommonResult<String> wechatPhone(@RequestBody WeChatPhoneReq req) {
        Long userId = UserContext.requireMemberId();
        String phone = weChatApiClient.getPhoneNumber(req.code());
        bindingService.bind(userId, phone);
        return CommonResult.ok(phone);
    }

    public record WeChatPhoneReq(String code) {
    }

    @PostMapping("/bind-phone")
    public CommonResult<Boolean> bind(@RequestBody BindReq req) {
        Long userId = UserContext.requireMemberId();
        bindingService.bind(userId, req.phone());
        return CommonResult.ok(true);
    }

    public record BindReq(String phone) {
    }
}
