package com.sancan.meal.member.user.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.member.user.dal.dataobject.MemberUserDO;
import com.sancan.meal.member.user.dal.mapper.MemberUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序「我的」基础信息。 */
@RestController
@RequestMapping("/app/me")
public class MemberMeController {

    private final MemberUserMapper memberUserMapper;

    public MemberMeController(MemberUserMapper memberUserMapper) {
        this.memberUserMapper = memberUserMapper;
    }

    public record MemberMeResp(Long id, String nickname, String avatar, String mobile) {
    }

    @GetMapping
    public CommonResult<MemberMeResp> me() {
        Long memberId = UserContext.requireMemberId();
        MemberUserDO user = memberUserMapper.selectById(memberId);
        if (user == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return CommonResult.ok(new MemberMeResp(user.getId(), user.getNickname(), user.getAvatar(), user.getMobile()));
    }
}
