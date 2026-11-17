package com.sancan.meal.audit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.audit.dal.dataobject.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {
}
