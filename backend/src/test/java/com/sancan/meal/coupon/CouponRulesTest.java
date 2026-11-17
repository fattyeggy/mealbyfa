package com.sancan.meal.coupon;

import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import com.sancan.meal.coupon.dal.dataobject.CouponTemplateDO;
import com.sancan.meal.coupon.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 手册 §16「优惠券：门槛 类型 有效期和一单一券正确」。 */
@SpringBootTest
@ActiveProfiles("test")
class CouponRulesTest {

    private static long seq = 7000;

    @Autowired
    private CouponService couponService;

    private long nextUser() {
        return ++seq;
    }

    private CouponDO grantCash(long userId, String threshold, String discount, int validDays) {
        Long templateId = couponService.createTemplate(new CouponService.TemplateReq(
                "满减券-" + seq, "CASH", new BigDecimal(threshold), new BigDecimal(discount),
                null, null, validDays));
        return couponService.grant(userId, templateId);
    }

    @Test
    @DisplayName("未达门槛不可用")
    void thresholdNotReached() {
        long userId = nextUser();
        CouponDO coupon = grantCash(userId, "50", "10", 30);
        assertThatThrownBy(() -> couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("40")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("满");
    }

    @Test
    @DisplayName("达到门槛按面额抵扣，且不超过订单金额")
    void discountApplied() {
        long userId = nextUser();
        CouponDO coupon = grantCash(userId, "50", "10", 30);
        assertThat(couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("60")))
                .isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("8")))
                .as("门槛 50 不满足时应拒绝而不是抵扣")
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("过期券不可用")
    void expiredCoupon() {
        long userId = nextUser();
        CouponDO coupon = grantCash(userId, "0", "5", 0);
        assertThatThrownBy(() -> couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("60")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("有效期");
    }

    @Test
    @DisplayName("一单一券：已核销的券不能再次使用")
    void usedCouponCannotBeReused() {
        long userId = nextUser();
        CouponDO coupon = grantCash(userId, "0", "5", 30);
        couponService.markUsed(coupon.getId(), 900001L);
        assertThatThrownBy(() -> couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("60")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不可用");
    }

    @Test
    @DisplayName("他人的券不可用")
    void otherUsersCouponRejected() {
        long owner = nextUser();
        long other = nextUser();
        CouponDO coupon = grantCash(owner, "0", "5", 30);
        assertThatThrownBy(() -> couponService.calculateDiscount(other, coupon.getId(), new BigDecimal("60")))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("锁单前取消后券恢复为可用")
    void releaseRestoresCoupon() {
        long userId = nextUser();
        CouponDO coupon = grantCash(userId, "0", "5", 30);
        couponService.markUsed(coupon.getId(), 900002L);
        couponService.release(coupon.getId());
        assertThat(couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("60")))
                .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("按比例折扣券受上限约束")
    void rateCouponCapped() {
        long userId = nextUser();
        Long templateId = couponService.createTemplate(new CouponService.TemplateReq(
                "八折券-" + seq, "RATE", BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("15.00"), 30));
        CouponDO coupon = couponService.grant(userId, templateId);
        // 100 × 20% = 20，但上限 15
        assertThat(couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("100")))
                .isEqualByComparingTo("15.00");
        assertThat(couponService.calculateDiscount(userId, coupon.getId(), new BigDecimal("50")))
                .isEqualByComparingTo("10.00");
    }
}
