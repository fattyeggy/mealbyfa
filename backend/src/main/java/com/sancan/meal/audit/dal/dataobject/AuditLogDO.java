package com.sancan.meal.audit.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 敏感操作审计（手册 §14）：credit 增减、退款、冲正、菜单发布、定价发布、复核处理。 */
@Data
@TableName("meal_audit_log")
public class AuditLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String operatorType;
    private Long operatorId;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}
