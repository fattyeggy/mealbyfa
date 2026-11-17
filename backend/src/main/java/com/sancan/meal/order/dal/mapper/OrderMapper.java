package com.sancan.meal.order.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {

    /** 锁单时对订单行加行锁，保证并发下同一订单只被处理一次。 */
    @Select("SELECT * FROM meal_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    OrderDO selectByIdForUpdate(@Param("id") Long id);
}
