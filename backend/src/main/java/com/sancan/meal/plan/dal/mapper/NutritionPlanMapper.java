package com.sancan.meal.plan.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.plan.dal.dataobject.NutritionPlanDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NutritionPlanMapper extends BaseMapper<NutritionPlanDO> {
}
