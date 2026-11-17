package com.sancan.meal.plan.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.audit.service.AuditLogService;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.plan.dal.dataobject.NutritionPlanDO;
import com.sancan.meal.plan.dal.dataobject.PlanReviewDO;
import com.sancan.meal.plan.dal.mapper.NutritionPlanMapper;
import com.sancan.meal.plan.dal.mapper.PlanReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 复核事项（P4.6）。
 *
 * <p>原则：复核事实以 {@code meal_plan_review} 为准，计划上的 {@code needs_review}
 * 只是汇总冗余。任何一条 PENDING 事项都必须阻断下单。
 */
@Service
public class PlanReviewService {

    private final PlanReviewMapper reviewMapper;
    private final NutritionPlanMapper planMapper;
    private final AuditLogService auditLogService;

    public PlanReviewService(PlanReviewMapper reviewMapper, NutritionPlanMapper planMapper,
                             AuditLogService auditLogService) {
        this.reviewMapper = reviewMapper;
        this.planMapper = planMapper;
        this.auditLogService = auditLogService;
    }

    /** 待落库的复核事项。 */
    public record Draft(String reviewType, LocalDate serviceDate, String mealType, String message) {
        public static Draft planLevel(String reviewType, String message) {
            return new Draft(reviewType, null, null, message);
        }

        public static Draft mealLevel(String reviewType, LocalDate date, String mealType, String message) {
            return new Draft(reviewType, date, mealType, message);
        }
    }

    public List<PlanReviewDO> list(Long planId, String status) {
        LambdaQueryWrapper<PlanReviewDO> wrapper = new LambdaQueryWrapper<PlanReviewDO>()
                .eq(PlanReviewDO::getPlanId, planId)
                .orderByAsc(PlanReviewDO::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(PlanReviewDO::getStatus, status.toUpperCase());
        }
        return reviewMapper.selectList(wrapper);
    }

    /** 全量复核列表（后台首页用），可按状态筛选。 */
    public List<PlanReviewDO> listAll(String status, int limit) {
        LambdaQueryWrapper<PlanReviewDO> wrapper = new LambdaQueryWrapper<PlanReviewDO>()
                .orderByAsc(PlanReviewDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(500, limit)));
        if (status != null && !status.isBlank()) {
            wrapper.eq(PlanReviewDO::getStatus, status.toUpperCase());
        }
        return reviewMapper.selectList(wrapper);
    }

    public List<PlanReviewDO> openItems(Long planId) {
        return list(planId, "PENDING");
    }

    public long openCount(Long planId) {
        Long count = reviewMapper.selectCount(new LambdaQueryWrapper<PlanReviewDO>()
                .eq(PlanReviewDO::getPlanId, planId)
                .eq(PlanReviewDO::getStatus, "PENDING"));
        return count == null ? 0 : count;
    }

    /**
     * 用最新一次评估结果替换某个范围内的 PENDING 事项。
     * 已 RESOLVED 的历史保留不动，保证审计可追溯。
     */
    @Transactional
    public void replaceScope(Long planId, Long userId, LocalDate serviceDate, String mealType,
                             String reviewType, List<Draft> drafts) {
        LambdaQueryWrapper<PlanReviewDO> wrapper = new LambdaQueryWrapper<PlanReviewDO>()
                .eq(PlanReviewDO::getPlanId, planId)
                .eq(PlanReviewDO::getStatus, "PENDING")
                .eq(PlanReviewDO::getReviewType, reviewType);
        if (serviceDate == null) {
            wrapper.isNull(PlanReviewDO::getServiceDate);
        } else {
            wrapper.eq(PlanReviewDO::getServiceDate, serviceDate);
        }
        if (mealType == null) {
            wrapper.isNull(PlanReviewDO::getMealType);
        } else {
            wrapper.eq(PlanReviewDO::getMealType, mealType);
        }
        reviewMapper.delete(wrapper);

        for (Draft draft : drafts) {
            PlanReviewDO review = new PlanReviewDO();
            review.setPlanId(planId);
            review.setUserId(userId);
            review.setServiceDate(draft.serviceDate());
            review.setMealType(draft.mealType());
            review.setReviewType(draft.reviewType());
            review.setReviewMessage(truncate(draft.message(), 500));
            review.setStatus("PENDING");
            reviewMapper.insert(review);
        }
        syncPlanFlag(planId);
    }

    /** 批量写入（生成计划时使用）。 */
    @Transactional
    public void addAll(Long planId, Long userId, List<Draft> drafts) {
        for (Draft draft : drafts) {
            PlanReviewDO review = new PlanReviewDO();
            review.setPlanId(planId);
            review.setUserId(userId);
            review.setServiceDate(draft.serviceDate());
            review.setMealType(draft.mealType());
            review.setReviewType(draft.reviewType());
            review.setReviewMessage(truncate(draft.message(), 500));
            review.setStatus("PENDING");
            reviewMapper.insert(review);
        }
        syncPlanFlag(planId);
    }

    /** 处理一条复核事项，并同步计划汇总状态。 */
    @Transactional
    public void resolve(Long reviewId, String note) {
        PlanReviewDO review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "复核事项不存在");
        }
        if ("RESOLVED".equals(review.getStatus())) {
            return;
        }
        review.setStatus("RESOLVED");
        review.setResolvedBy(currentOperatorId());
        review.setResolvedTime(LocalDateTime.now());
        review.setResolutionNote(note);
        reviewMapper.updateById(review);
        syncPlanFlag(review.getPlanId());
        auditLogService.record("REVIEW_RESOLVE", "PLAN_REVIEW", reviewId,
                review.getReviewType() + "：" + (note == null ? "" : note));
    }

    /** 复核事项变化后，重算计划的汇总标记与摘要文本。 */
    @Transactional
    public void syncPlanFlag(Long planId) {
        NutritionPlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            return;
        }
        List<PlanReviewDO> open = openItems(planId);
        plan.setNeedsReview(open.isEmpty() ? 0 : 1);
        plan.setReviewNote(open.isEmpty() ? null : summary(open));
        planMapper.updateById(plan);
    }

    public String summary(List<PlanReviewDO> reviews) {
        List<String> parts = new ArrayList<>();
        for (PlanReviewDO review : reviews) {
            String scope = review.getServiceDate() == null ? ""
                    : review.getServiceDate() + (review.getMealType() == null ? " " : " " + mealLabel(review.getMealType()) + " ");
            parts.add(scope + review.getReviewMessage());
        }
        return truncate(String.join("；", parts), 500);
    }

    private String mealLabel(String mealType) {
        try {
            return com.sancan.meal.menu.enums.MealType.valueOf(mealType).getLabel();
        } catch (IllegalArgumentException ex) {
            return mealType;
        }
    }

    private Long currentOperatorId() {
        String actor = UserContext.currentActor();
        String[] parts = actor.split(":", 2);
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
