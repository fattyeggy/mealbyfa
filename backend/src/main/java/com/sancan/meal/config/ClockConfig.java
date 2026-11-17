package com.sancan.meal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** 统一时区：下单截止、锁单任务与「明天」的判断都依赖北京时间。 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
