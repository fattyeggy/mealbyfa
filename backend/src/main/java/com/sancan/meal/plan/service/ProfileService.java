package com.sancan.meal.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.menu.service.AllergenResolver;
import com.sancan.meal.plan.controller.dto.OnboardingReq;
import com.sancan.meal.plan.controller.dto.ProfileResp;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.dal.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Onboarding 建档（手册 §5「未注册到个人计划」第 4 步）。 */
@Service
public class ProfileService {

    private final UserProfileMapper userProfileMapper;

    public ProfileService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    public UserProfileDO find(Long userId) {
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileDO>()
                .eq(UserProfileDO::getUserId, userId));
    }

    /** 取已完成的档案；未建档或未完成时抛出业务异常，由调用方转成引导语。 */
    public UserProfileDO requireCompleted(Long userId) {
        UserProfileDO profile = find(userId);
        if (profile == null || profile.getOnboardingStatus() == null || profile.getOnboardingStatus() != 1) {
            throw new ServiceException(ErrorCode.PROFILE_INCOMPLETE);
        }
        return profile;
    }

    @Transactional
    public ProfileResp save(Long userId, OnboardingReq req) {
        UserProfileDO profile = find(userId);
        if (profile == null) {
            profile = new UserProfileDO();
            profile.setUserId(userId);
        }
        profile.setGender(req.gender().trim().toUpperCase());
        profile.setBirthDate(req.birthDate());
        profile.setHeightCm(req.heightCm());
        profile.setWeightKg(req.weightKg());
        profile.setTargetWeightKg(req.targetWeightKg());
        profile.setActivityLevel(req.activityLevel().trim().toUpperCase());
        profile.setGoal(req.goal().trim().toUpperCase());
        profile.setTrainingState(req.trainingState());
        profile.setAppetiteLevel(req.appetiteLevel().trim().toUpperCase());
        profile.setDislikes(req.dislikes());
        profile.setAllergies(req.allergies());
        profile.setProteinPref(req.proteinPref());
        if (req.contactName() != null && !req.contactName().isBlank()) {
            profile.setContactName(req.contactName());
        }
        if (req.contactPhone() != null && !req.contactPhone().isBlank()) {
            profile.setContactPhone(req.contactPhone());
        }
        if (req.address() != null && !req.address().isBlank()) {
            profile.setAddress(req.address());
        }
        profile.setOnboardingStatus(1);
        profile.setOnboardingTime(LocalDateTime.now());

        if (profile.getId() == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        return toResp(profile);
    }

    /** 下单时保存默认配送信息，下次自动预填。 */
    public void saveContact(Long userId, String name, String phone, String address) {
        UserProfileDO profile = find(userId);
        if (profile == null) {
            return;
        }
        profile.setContactName(name);
        profile.setContactPhone(phone);
        profile.setAddress(address);
        userProfileMapper.updateById(profile);
    }

    public ProfileResp toResp(UserProfileDO profile) {
        if (profile == null) {
            return new ProfileResp(null, null, null, null, null, null, null, null,
                    null, null, null, List.of(), null, null, null, null, false, null);
        }
        return new ProfileResp(
                profile.getUserId(), profile.getGender(), profile.getBirthDate(),
                profile.getHeightCm(), profile.getWeightKg(), profile.getTargetWeightKg(),
                profile.getActivityLevel(), profile.getGoal(), profile.getTrainingState(),
                profile.getAppetiteLevel(), profile.getDislikes(),
                profile.getAllergies() == null ? List.of() : AllergenResolver.split(profile.getAllergies()),
                profile.getProteinPref(),
                profile.getContactName(), profile.getContactPhone(), profile.getAddress(),
                profile.getOnboardingStatus() != null && profile.getOnboardingStatus() == 1,
                profile.getOnboardingTime());
    }
}
