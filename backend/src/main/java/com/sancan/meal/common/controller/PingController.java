package com.sancan.meal.common.controller;

import com.sancan.meal.common.api.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 健康检查，部署与探活使用。 */
@RestController
public class PingController {

    private static final LocalDateTime STARTED_AT = LocalDateTime.now();

    @GetMapping("/ping")
    public CommonResult<Map<String, Object>> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("app", "sancan-meal-service");
        body.put("startedAt", STARTED_AT.toString());
        body.put("time", LocalDateTime.now().toString());
        return CommonResult.ok(body);
    }
}
