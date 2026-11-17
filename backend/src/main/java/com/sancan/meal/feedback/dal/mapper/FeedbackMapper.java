package com.sancan.meal.feedback.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.feedback.dal.dataobject.FeedbackDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedbackMapper extends BaseMapper<FeedbackDO> {
}
