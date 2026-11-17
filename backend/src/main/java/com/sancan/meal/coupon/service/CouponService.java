package com.sancan.meal.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import com.sancan.meal.coupon.dal.dataobject.CouponTemplateDO;
import com.sancan.meal.coupon.dal.mapper.CouponMapper;
import com.sancan.meal.coupon.dal.mapper.CouponTemplateMapper;
import com.sancan.meal.coupon.enums.CouponStatus;
import com.sancan.meal.coupon.enums.CouponType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 优惠券（手册 §7「优惠券 MVP」）。
 *
 * <ul>
 *   <li>每个订单最多使用一张券，不叠加；</li>
 *   <li>锁单前取消且券仍在有效期时原路退回；</li>
 *   <li>券抵扣部分不产生正式积分。</li>
 * </ul>
 */
@Service
public class CouponService {

    private final CouponTemplateMapper templateMapper;
    private final CouponMapper couponMapper;

    public CouponService(CouponTemplateMapper templateMapper, CouponMapper couponMapper) {
        this.templateMapper = templateMapper;
        this.couponMapper = couponMapper;
    }

    public record TemplateReq(String name, String type, BigDecimal thresholdAmount,
                              BigDecimal discountAmount, BigDecimal discountRate,
                              BigDecimal maxDiscountAmount, Integer validDays) {
    }

    @Transactional
    public Long createTemplate(TemplateReq req) {
        CouponTemplateDO template = new CouponTemplateDO();
        template.setName(req.name());
        CouponType type = req.type() == null ? CouponType.CASH : CouponType.valueOf(req.type().toUpperCase());
        template.setType(type.name());
        template.setThresholdAmount(req.thresholdAmount() == null ? BigDecimal.ZERO : req.thresholdAmount());
        template.setDiscountAmount(req.discountAmount() == null ? BigDecimal.ZERO : req.discountAmount());
        template.setDiscountRate(req.discountRate());
        template.setMaxDiscountAmount(req.maxDiscountAmount());
        template.setValidDays(req.validDays() == null ? 30 : req.validDays());
        template.setStatus(0);
        templateMapper.insert(template);
        return template.getId();
    }

    public List<CouponTemplateDO> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<CouponTemplateDO>()
                .orderByDesc(CouponTemplateDO::getId));
    }

    /** 发券给指定用户（阶段一由管理员发放）。 */
    @Transactional
    public CouponDO grant(Long userId, Long templateId) {
        CouponTemplateDO template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new ServiceException(ErrorCode.COUPON_NOT_FOUND, "券模板不存在：" + templateId);
        }
        CouponDO coupon = new CouponDO();
        coupon.setTemplateId(templateId);
        coupon.setUserId(userId);
        coupon.setCode("C" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        coupon.setStatus(CouponStatus.UNUSED.name());
        coupon.setStartTime(LocalDateTime.now());
        coupon.setEndTime(LocalDateTime.now().plusDays(template.getValidDays()));
        couponMapper.insert(coupon);
        return coupon;
    }

    public List<CouponDO> listUserCoupons(Long userId, String status) {
        LambdaQueryWrapper<CouponDO> wrapper = new LambdaQueryWrapper<CouponDO>()
                .eq(CouponDO::getUserId, userId)
                .orderByDesc(CouponDO::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(CouponDO::getStatus, status.toUpperCase());
        }
        return couponMapper.selectList(wrapper);
    }

    /** 小程序展示用的券视图（带模板名称、门槛与面额）。 */
    public record CouponView(Long id, String name, String type, BigDecimal thresholdAmount,
                             BigDecimal discountAmount, BigDecimal discountRate,
                             BigDecimal maxDiscountAmount, LocalDateTime endTime, String status) {
    }

    /** 用户券列表（含模板信息），供下单页选券。 */
    public List<CouponView> listViews(Long userId, String status) {
        List<CouponDO> coupons = listUserCoupons(userId, status);
        if (coupons.isEmpty()) {
            return List.of();
        }
        Map<Long, CouponTemplateDO> templates = templateMapper.selectBatchIds(
                        coupons.stream().map(CouponDO::getTemplateId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CouponTemplateDO::getId, t -> t));
        return coupons.stream().map(c -> {
            CouponTemplateDO t = templates.get(c.getTemplateId());
            return new CouponView(c.getId(), t == null ? "优惠券" : t.getName(),
                    t == null ? null : t.getType(),
                    t == null ? BigDecimal.ZERO : t.getThresholdAmount(),
                    t == null ? BigDecimal.ZERO : t.getDiscountAmount(),
                    t == null ? null : t.getDiscountRate(),
                    t == null ? null : t.getMaxDiscountAmount(),
                    c.getEndTime(), c.getStatus());
        }).toList();
    }

    public CouponDO get(Long couponId) {
        CouponDO coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new ServiceException(ErrorCode.COUPON_NOT_FOUND);
        }
        return coupon;
    }

    /**
     * 校验并计算抵扣金额。
     *
     * @param orderAmount 订单原价
     * @return 实际抵扣金额，不超过订单原价
     */
    public BigDecimal calculateDiscount(Long userId, Long couponId, BigDecimal orderAmount) {
        CouponDO coupon = get(couponId);
        if (!coupon.getUserId().equals(userId)) {
            throw new ServiceException(ErrorCode.COUPON_NOT_FOUND);
        }
        if (!CouponStatus.UNUSED.name().equals(coupon.getStatus())) {
            throw new ServiceException(ErrorCode.COUPON_UNAVAILABLE, "优惠券状态不可用：" + coupon.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new ServiceException(ErrorCode.COUPON_UNAVAILABLE, "优惠券不在有效期内");
        }
        CouponTemplateDO template = templateMapper.selectById(coupon.getTemplateId());
        if (template == null || (template.getStatus() != null && template.getStatus() != 0)) {
            throw new ServiceException(ErrorCode.COUPON_UNAVAILABLE, "优惠券模板不可用");
        }
        if (orderAmount.compareTo(template.getThresholdAmount()) < 0) {
            throw new ServiceException(ErrorCode.COUPON_THRESHOLD_NOT_REACHED,
                    "订单需满 " + template.getThresholdAmount() + " 元才能使用该券");
        }
        BigDecimal discount;
        if (CouponType.RATE.name().equals(template.getType())) {
            BigDecimal rate = template.getDiscountRate() == null ? BigDecimal.ZERO : template.getDiscountRate();
            discount = orderAmount.multiply(rate);
            if (template.getMaxDiscountAmount() != null) {
                discount = discount.min(template.getMaxDiscountAmount());
            }
        } else {
            discount = template.getDiscountAmount();
        }
        discount = discount.max(BigDecimal.ZERO).min(orderAmount).setScale(2, RoundingMode.HALF_UP);
        return discount;
    }

    /** 锁单核销。 */
    @Transactional
    /**
     * 下单预占：条件更新，影响行数为 0 表示券已被占用/过期/不属于该用户。
     * 调用方（下单）不得依赖此前的查询结果，必须以本方法的返回值为准。
     */
    public void reserve(Long userId, Long couponId, Long orderId) {
        int rows = couponMapper.reserve(couponId, userId, orderId);
        if (rows == 0) {
            throw new ServiceException(ErrorCode.COUPON_UNAVAILABLE,
                    "优惠券已被使用或已过期，请重新选择");
        }
    }

    /** 锁单核销：只允许本订单的 RESERVED → USED。 */
    public void consume(Long couponId, Long orderId) {
        int rows = couponMapper.consume(couponId, orderId);
        if (rows == 0) {
            throw new ServiceException(ErrorCode.COUPON_UNAVAILABLE,
                    "优惠券状态异常（可能已过期或不属于本订单）");
        }
    }

    /** 取消/退款释放：只释放本订单占用的券。 */
    public void releaseForOrder(Long couponId, Long orderId) {
        if (couponId != null) {
            couponMapper.release(couponId, orderId);
        }
    }

    public void markUsed(Long couponId, Long orderId) {
        CouponDO coupon = get(couponId);
        coupon.setStatus(CouponStatus.USED.name());
        coupon.setUsedOrderId(orderId);
        coupon.setUsedTime(LocalDateTime.now());
        couponMapper.updateById(coupon);
    }

    /** 锁单前取消：券原路退回可用状态。 */
    @Transactional
    public void release(Long couponId) {
        if (couponId == null) {
            return;
        }
        CouponDO coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            return;
        }
        coupon.setStatus(CouponStatus.UNUSED.name());
        coupon.setUsedOrderId(null);
        coupon.setUsedTime(null);
        couponMapper.updateById(coupon);
    }
}
