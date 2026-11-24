package com.sancan.meal.cook.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.cook.dal.dataobject.CookRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CookRecordMapper extends BaseMapper<CookRecordDO> {
}
