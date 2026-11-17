package com.sancan.meal.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sancan.meal.audit.dal.dataobject.AuditLogDO;
import com.sancan.meal.audit.dal.mapper.AuditLogMapper;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志。写入失败只记录告警，不影响业务事务——
 * 但敏感操作本身已有账本流水，审计是第二道留痕。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(String action, String targetType, Object targetId, String detail) {
        try {
            String actor = UserContext.currentActor();
            String[] parts = actor.split(":", 2);
            AuditLogDO entry = new AuditLogDO();
            entry.setOperatorType(parts[0]);
            entry.setOperatorId(parts.length > 1 ? parse(parts[1]) : null);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId == null ? null : String.valueOf(targetId));
            entry.setDetail(detail);
            entry.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("审计日志写入失败 action={} target={}", action, targetId, ex);
        }
    }

    public PageResult<AuditLogDO> page(String action, int pageNo, int pageSize) {
        LambdaQueryWrapper<AuditLogDO> wrapper = new LambdaQueryWrapper<AuditLogDO>()
                .orderByDesc(AuditLogDO::getId);
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLogDO::getAction, action.toUpperCase());
        }
        Page<AuditLogDO> page = auditLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    private Long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
