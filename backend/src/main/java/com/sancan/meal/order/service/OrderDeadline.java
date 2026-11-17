package com.sancan.meal.order.service;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 下单截止：服务日前一天 12:00（北京时间）。
 *
 * <p>把规则抽成独立组件，便于用固定时钟验证 11:59 / 12:00 / 12:01 三个边界。
 */
@Component
public class OrderDeadline {

    private final Clock clock;
    private final int cutoffHour;

    public OrderDeadline(Clock clock, @Value("${meal.order.cutoff-hour:12}") int cutoffHour) {
        this.clock = clock;
        this.cutoffHour = cutoffHour;
    }

    /** 该服务日是否已截止。 */
    public boolean isClosed(LocalDate serviceDate) {
        return !now().isBefore(deadlineOf(serviceDate));
    }

    public LocalDateTime deadlineOf(LocalDate serviceDate) {
        return LocalDateTime.of(serviceDate.minusDays(1), LocalTime.of(cutoffHour, 0));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 下单/改单前的统一校验。 */
    public void requireOpen(LocalDate serviceDate) {
        if (isClosed(serviceDate)) {
            throw new ServiceException(ErrorCode.ORDER_STATUS_INVALID,
                    "服务日 " + serviceDate + " 已于 " + deadlineOf(serviceDate).toLocalDate()
                            + " " + cutoffHour + ":00 截止下单");
        }
    }
}
