package com.sancan.meal.order;

import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.order.service.OrderDeadline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 整改 P1：服务日前一天 12:00（北京时间）截止，11:59/12:00/12:01 三边界。 */
class OrderDeadlineTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 20);

    private OrderDeadline at(String time) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-19T" + time + ":00Z").plusSeconds(0), ZONE);
        return new OrderDeadline(clock, 12);
    }

    private OrderDeadline beijingTime(String localTime) {
        // 北京时间 = UTC+8，直接构造 ZonedDateTime 更直观
        var zoned = java.time.ZonedDateTime.of(
                LocalDate.of(2026, 11, 19),
                java.time.LocalTime.parse(localTime), ZONE);
        return new OrderDeadline(Clock.fixed(zoned.toInstant(), ZONE), 12);
    }

    @Test
    @DisplayName("11:59 仍可下单")
    void beforeDeadline() {
        assertThat(beijingTime("11:59:00").isClosed(SERVICE_DATE)).isFalse();
        assertThatCode(() -> beijingTime("11:59:59").requireOpen(SERVICE_DATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("12:00 起禁止下单")
    void atDeadline() {
        assertThat(beijingTime("12:00:00").isClosed(SERVICE_DATE)).isTrue();
        assertThatThrownBy(() -> beijingTime("12:00:00").requireOpen(SERVICE_DATE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("截止");
    }

    @Test
    @DisplayName("12:01 仍然禁止")
    void afterDeadline() {
        assertThat(beijingTime("12:01:00").isClosed(SERVICE_DATE)).isTrue();
    }

    @Test
    @DisplayName("截止时间按北京时间解释，不受服务器时区影响")
    void usesBeijingZone() {
        OrderDeadline deadline = new OrderDeadline(Clock.system(ZONE), 12);
        assertThat(deadline.deadlineOf(SERVICE_DATE))
                .isEqualTo(java.time.LocalDateTime.of(2026, 11, 19, 12, 0));
    }
}
