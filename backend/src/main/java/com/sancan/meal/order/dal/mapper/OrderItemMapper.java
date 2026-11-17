package com.sancan.meal.order.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.order.dal.dataobject.OrderItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemDO> {
}
