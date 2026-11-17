package com.sancan.meal.plan.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 用户档案：Onboarding 采集的基础数据、目标、训练、食量、忌口、过敏与蛋白偏好。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_user_profile")
public class UserProfileDO extends BaseDO {

    private Long userId;
    private String gender;
    private LocalDate birthDate;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal targetWeightKg;
    private String activityLevel;
    private String goal;
    private String trainingState;
    private String appetiteLevel;
    /** 忌口，逗号分隔。 */
    private String dislikes;
    /** 过敏原，逗号分隔；下单与换蛋白时用于拦截。 */
    private String allergies;
    /** 蛋白偏好，逗号分隔，如 鸡胸肉、鱼、虾、牛肉。 */
    private String proteinPref;
    /** 默认配送信息：下单时预填，避免每次重复输入。 */
    private String contactName;
    private String contactPhone;
    private String address;
    private java.math.BigDecimal bodyFatPct;
    private String dailyActivity;
    private Integer workoutsPerWeek;
    private Integer workoutMinutes;
    private String workoutType;
    private String workoutIntensity;
    private String healthFlags;
    private String dietRestrictions;
    private String orderedMeals;
    private String tastePrefs;
    private Integer targetProteinTier;
    private Integer targetKcal;
    private Integer targetFatG;
    private Integer targetCarbG;
    private String nutritionConfigVersion;
    /** 0 未完成，1 已完成。 */
    private Integer onboardingStatus;
    private LocalDateTime onboardingTime;
}
