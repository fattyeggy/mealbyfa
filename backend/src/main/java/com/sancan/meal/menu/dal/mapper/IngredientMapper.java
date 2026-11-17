package com.sancan.meal.menu.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IngredientMapper extends BaseMapper<IngredientDO> {
}
