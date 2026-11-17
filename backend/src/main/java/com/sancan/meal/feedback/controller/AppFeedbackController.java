package com.sancan.meal.feedback.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.feedback.dal.dataobject.FeedbackDO;
import com.sancan.meal.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 小程序完餐反馈与周复盘（手册 §5）。 */
@RestController
@RequestMapping("/app")
public class AppFeedbackController {

    private final FeedbackService feedbackService;

    public AppFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/orders/{orderId}/feedback")
    public CommonResult<FeedbackDO> submit(@PathVariable Long orderId, @Valid @RequestBody FeedbackReq req) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(feedbackService.submit(userId, orderId,
                new FeedbackService.SubmitReq(req.mealType(), req.leftoverRatio(),
                        req.satiety(), req.trainingState(), req.content())));
    }

    @GetMapping("/feedback/week")
    public CommonResult<FeedbackService.WeekResp> week(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = UserContext.requireMemberId();
        return CommonResult.ok(feedbackService.week(userId, date == null ? LocalDate.now() : date));
    }

    public record FeedbackReq(@NotBlank(message = "餐次不能为空") String mealType,
                              @NotBlank(message = "请选择剩余情况") String leftoverRatio,
                              Integer satiety,
                              String trainingState,
                              String content) {
    }
}
