package com.sancan.meal.coupon.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 券状态变更全部走条件更新，避免「先查后改」的并发漏洞。
 * 影响行数为 0 即表示抢券/核销/释放失败，由服务层抛错。
 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponDO> {

    /** 下单预占：只有该用户、未使用、未过期的券才能预占到本订单。 */
    @Update("""
            UPDATE meal_coupon
               SET status = 'RESERVED', used_order_id = #{orderId}, used_time = CURRENT_TIMESTAMP,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{couponId}
               AND user_id = #{userId}
               AND status = 'UNUSED'
               AND end_time > CURRENT_TIMESTAMP
            """)
    int reserve(@Param("couponId") Long couponId, @Param("userId") Long userId,
                @Param("orderId") Long orderId);

    /** 锁单核销：只允许本订单的 RESERVED → USED，且仍在有效期内。 */
    @Update("""
            UPDATE meal_coupon
               SET status = 'USED', used_time = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
             WHERE id = #{couponId}
               AND used_order_id = #{orderId}
               AND status = 'RESERVED'
               AND end_time > CURRENT_TIMESTAMP
            """)
    int consume(@Param("couponId") Long couponId, @Param("orderId") Long orderId);

    /** 释放：只能释放本订单占用的券（取消/退款）。 */
    @Update("""
            UPDATE meal_coupon
               SET status = 'UNUSED', used_order_id = NULL, used_time = NULL,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{couponId}
               AND used_order_id = #{orderId}
               AND status IN ('RESERVED', 'USED')
            """)
    int release(@Param("couponId") Long couponId, @Param("orderId") Long orderId);
}
