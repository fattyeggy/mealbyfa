package com.sancan.meal.order.job;

import com.sancan.meal.config.MealProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日 12:00 锁次日订单（手册 §8「前一日 12 点定时锁单」）。
 * 时间表达式来自 {@code meal.scheduling.lock-cron}，默认 {@code 0 0 12 * * ?}。
 */
@Component
public class LockScheduler {

    private static final Logger log = LoggerFactory.getLogger(LockScheduler.class);

    private final OrderLockJob orderLockJob;
    private final MealProperties properties;

    public LockScheduler(OrderLockJob orderLockJob, MealProperties properties) {
        this.orderLockJob = orderLockJob;
        this.properties = properties;
    }

    @Scheduled(cron = "${meal.scheduling.lock-cron:0 0 12 * * ?}", zone = "Asia/Shanghai")
    public void lockTomorrowOrders() {
        if (!properties.getScheduling().isLockEnabled()) {
            log.info("锁单任务已关闭，跳过");
            return;
        }
        LocalDate target = LocalDate.now().plusDays(1);
        log.info("开始锁单，目标服务日 {}", target);
        orderLockJob.run(target);
    }
}
