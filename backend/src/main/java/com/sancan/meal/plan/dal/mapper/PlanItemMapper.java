package com.sancan.meal.plan.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.plan.dal.dataobject.PlanItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlanItemMapper extends BaseMapper<PlanItemDO> {
}
