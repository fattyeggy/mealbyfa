package com.sancan.meal.order.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.order.dal.dataobject.DeliveryItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliveryItemMapper extends BaseMapper<DeliveryItemDO> {
}
