package com.sancan.meal.order.dal.mapper;

import com.sancan.meal.order.dal.dataobject.OrderSnapshotDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 快照只允许插入与查询：不暴露 update/delete，
 * 从数据访问层保证「历史订单内容不可被改写」。
 */
@Mapper
public interface OrderSnapshotMapper {

    @Insert("""
            INSERT INTO meal_order_snapshot (order_id, menu_id, menu_version, total_price, payload, create_time)
            VALUES (#{orderId}, #{menuId}, #{menuVersion}, #{totalPrice}, #{payload}, #{createTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderSnapshotDO snapshot);

    @Select("""
            SELECT id, order_id, menu_id, menu_version, total_price, payload, create_time
              FROM meal_order_snapshot WHERE order_id = #{orderId} LIMIT 1
            """)
    OrderSnapshotDO selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT COUNT(*) FROM meal_order_snapshot WHERE order_id = #{orderId}")
    long countByOrderId(@Param("orderId") Long orderId);
}
