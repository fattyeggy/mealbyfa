package com.sancan.meal.admin.onboarding;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.member.auth.service.MemberClaimCodes;
import com.sancan.meal.member.user.dal.dataobject.MemberUserDO;
import com.sancan.meal.member.user.dal.mapper.MemberUserMapper;
import com.sancan.meal.nutrition.controller.OnboardingReqV2;
import com.sancan.meal.nutrition.service.OnboardingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台按手机号直接建档：用户只提供手机号，运营录入档案。 */
@Service
public class AdminMemberOnboardingService {

    private final MemberUserMapper memberUserMapper;
    private final OnboardingService onboardingService;
    private final MemberClaimCodes claimCodes;

    public AdminMemberOnboardingService(MemberUserMapper memberUserMapper,
                                        OnboardingService onboardingService,
                                        MemberClaimCodes claimCodes) {
        this.memberUserMapper = memberUserMapper;
        this.onboardingService = onboardingService;
        this.claimCodes = claimCodes;
    }

    public record Req(String phone, String nickname, OnboardingReqV2 profile) {
    }

    public record Resp(Long memberId, String phone, boolean blocked, String reason,
                       Double bmr, Double activityFactor, Double tdee, Integer kcal,
                       Integer proteinRaw, Integer proteinTier, Integer fatG, Integer carbG,
                       /** 认领码：发给本人，用户在小程序手动绑定手机号时输入。 */
                       String claimCode) {
    }

    @Transactional
    public Resp onboard(Req req) {
        String phone = normalize(req.phone());
        MemberUserDO member = memberUserMapper.selectByMobile(phone);
        if (member == null) {
            member = new MemberUserDO();
            member.setOpenid("admin_" + phone);
            member.setMobile(phone);
            member.setNickname(req.nickname() == null || req.nickname().isBlank()
                    ? "用户" + phone.substring(phone.length() - 4) : req.nickname());
            member.setStatus(0);
            memberUserMapper.insert(member);
        } else if (member.getOpenid() == null || !member.getOpenid().startsWith("admin_")) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该手机号已绑定微信，请直接在后台查询");
        }
        OnboardingService.Outcome outcome = onboardingService.submit(member.getId(), req.profile());
        var r = outcome.result();
        if (outcome.blocked()) {
            return new Resp(member.getId(), phone, true, outcome.reason(), null, null, null,
                    null, null, null, null, null, claimCodes.codeFor(member.getId(), phone));
        }
        return new Resp(member.getId(), phone, false, null,
                r.bmr(), r.activityFactor(), r.tdee(), r.kcal(),
                r.proteinRaw(), r.proteinTier(), r.fatG(), r.carbG(),
                claimCodes.codeFor(member.getId(), phone));
    }

    private String normalize(String phone) {
        String value = phone == null ? "" : phone.replaceAll("\\s", "");
        if (!value.matches("1\\d{10}")) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        return value;
    }
}
