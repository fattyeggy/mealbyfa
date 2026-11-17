package com.sancan.meal.nutrition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.nutrition.controller.OnboardingReqV2;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.dal.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 建档 + 计算引擎落库（开发说明第 2–7 节）。 */
@Service
public class OnboardingService {

    private final UserProfileMapper profileMapper;
    private final NutritionEngine.Config config = NutritionEngine.defaultConfig();
    private final ObjectMapper objectMapper;

    public OnboardingService(UserProfileMapper profileMapper, ObjectMapper objectMapper) {
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
    }

    /** 计算结果 + 是否拦截。 */
    public record Outcome(boolean blocked, String reason, String action, NutritionEngine.Result result) {
    }

    @Transactional
    public Outcome submit(Long userId, OnboardingReqV2 req) {
        validate(req);
        NutritionEngine.Input input = new NutritionEngine.Input(
                req.goal(), req.sex(), req.age(),
                req.heightCm().doubleValue(), req.weightKg().doubleValue(),
                req.bodyFatPct() == null ? null : req.bodyFatPct().doubleValue(),
                req.dailyActivity(),
                req.workoutsPerWeek() == null ? 0 : req.workoutsPerWeek(),
                req.workoutMinutes() == null ? 0 : req.workoutMinutes(),
                req.workoutIntensity() == null ? "medium" : req.workoutIntensity(),
                req.healthFlags() == null ? Set.of() : Set.copyOf(req.healthFlags()));

        NutritionEngine.Result result = NutritionEngine.calculate(input, config);

        UserProfileDO profile = profileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserProfileDO>()
                        .eq(UserProfileDO::getUserId, userId));
        if (profile == null) {
            profile = new UserProfileDO();
            profile.setUserId(userId);
        }
        profile.setGender("male".equals(req.sex()) ? "MALE" : "FEMALE");
        profile.setBirthDate(req.birthDate() != null ? req.birthDate()
                : LocalDate.now().minusYears(req.age()));
        profile.setHeightCm(req.heightCm());
        profile.setWeightKg(req.weightKg());
        profile.setTargetWeightKg(req.targetWeightKg());
        profile.setGoal(legacyGoal(req.goal()));
        profile.setActivityLevel(legacyActivity(req.dailyActivity()));
        profile.setBodyFatPct(req.bodyFatPct());
        profile.setDailyActivity(req.dailyActivity());
        profile.setWorkoutsPerWeek(req.workoutsPerWeek());
        profile.setWorkoutMinutes(req.workoutMinutes());
        profile.setWorkoutType(req.workoutType());
        profile.setWorkoutIntensity(req.workoutIntensity());
        profile.setHealthFlags(join(req.healthFlags()));
        profile.setAllergies(join(req.allergies()));
        profile.setDietRestrictions(join(req.dietRestrictions()));
        profile.setOrderedMeals(join(req.orderedMeals() == null ? List.of("breakfast", "lunch", "dinner")
                : req.orderedMeals()));
        profile.setTastePrefs(toJson(req.tastePrefs()));
        profile.setContactName(req.contactName());
        profile.setContactPhone(req.contactPhone());
        profile.setAddress(req.address());
        if (!result.blocked()) {
            profile.setTargetProteinTier(result.proteinTier());
            profile.setTargetKcal(result.kcal());
            profile.setTargetFatG(result.fatG());
            profile.setTargetCarbG(result.carbG());
            profile.setNutritionConfigVersion(config.version());
            profile.setOnboardingStatus(1);
            profile.setOnboardingTime(LocalDateTime.now());
        }
        if (profile.getId() == null) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
        return new Outcome(result.blocked(), result.blockReason(), result.action(), result);
    }

    private void validate(OnboardingReqV2 req) {
        if (req.workoutsPerWeek() != null && req.workoutsPerWeek() > 0
                && (req.workoutMinutes() == null || req.workoutMinutes() < 10
                    || req.workoutMinutes() > 180)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "单次运动时长需在 10–180 分钟");
        }
        if (req.orderedMeals() == null || req.orderedMeals().isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请至少选择一个订购餐次");
        }
        if (req.healthFlags() == null || req.healthFlags().isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请完成健康筛查");
        }
        if (req.healthFlags().contains("none") && req.healthFlags().size() > 1) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "「以上皆无」不能与其他选项同时选择");
        }
        if (Boolean.FALSE.equals(req.privacyAccepted())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请先同意隐私授权");
        }
    }

    private String legacyGoal(String goal) {
        return switch (goal) {
            case "cut" -> "LOSE";
            case "bulk" -> "GAIN";
            default -> "MAINTAIN";
        };
    }

    private String legacyActivity(String activity) {
        return switch (activity) {
            case "active" -> "HIGH";
            case "light" -> "LIGHT";
            default -> "SEDENTARY";
        };
    }

    private String join(List<String> values) {
        return values == null ? null : String.join(",", values);
    }

    private String toJson(OnboardingReqV2.TastePrefs prefs) {
        if (prefs == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("spice", prefs.spice());
        map.put("staple", prefs.staple());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
}
