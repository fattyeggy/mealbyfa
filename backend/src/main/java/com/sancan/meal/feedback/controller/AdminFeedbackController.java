package com.sancan.meal.feedback.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.feedback.dal.dataobject.FeedbackDO;
import com.sancan.meal.feedback.service.FeedbackService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 后台查看反馈，供周复盘使用。 */
@RestController
@RequestMapping("/admin/meal/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @RequirePermission(Permission.REVIEW_READ)
    @GetMapping
    public CommonResult<List<FeedbackDO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return CommonResult.ok(feedbackService.adminList(serviceDate));
    }
}
