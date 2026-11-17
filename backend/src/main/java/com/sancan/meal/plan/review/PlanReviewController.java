package com.sancan.meal.plan.review;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.plan.dal.dataobject.PlanReviewDO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台复核处理（P4.6 §3.2 第 5 条：必须先有受控处理入口）。 */
@RestController
@RequestMapping("/admin/meal")
public class PlanReviewController {

    private final PlanReviewService planReviewService;

    public PlanReviewController(PlanReviewService planReviewService) {
        this.planReviewService = planReviewService;
    }

    @RequirePermission(Permission.REVIEW_READ)
    @GetMapping("/reviews")
    public CommonResult<List<PlanReviewDO>> listAll(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return CommonResult.ok(planReviewService.listAll(status, limit));
    }

    @RequirePermission(Permission.REVIEW_READ)
    @GetMapping("/plans/{planId}/reviews")
    public CommonResult<List<PlanReviewDO>> list(@PathVariable Long planId,
                                                @RequestParam(required = false) String status) {
        return CommonResult.ok(planReviewService.list(planId, status));
    }

    @RequirePermission(Permission.REVIEW_WRITE)
    @PostMapping("/reviews/{id}/resolve")
    public CommonResult<Boolean> resolve(@PathVariable Long id, @Valid @RequestBody ResolveReq req) {
        planReviewService.resolve(id, req.note());
        return CommonResult.ok(true);
    }

    public record ResolveReq(String note) {
    }
}
