package com.sancan.meal.kitchen.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.kitchen.dal.dataobject.ProductionBatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionBatchMapper extends BaseMapper<ProductionBatchDO> {
}
