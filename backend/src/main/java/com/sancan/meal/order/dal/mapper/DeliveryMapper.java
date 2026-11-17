package com.sancan.meal.order.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.order.dal.dataobject.DeliveryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliveryMapper extends BaseMapper<DeliveryDO> {
}
