package com.sancan.meal.audit.controller;

import com.sancan.meal.audit.dal.dataobject.AuditLogDO;
import com.sancan.meal.audit.service.AuditLogService;
import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台审计查询。 */
@RestController
@RequestMapping("/admin/meal/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @RequirePermission(Permission.AUDIT_READ)
    @GetMapping
    public CommonResult<PageResult<AuditLogDO>> page(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "50") int pageSize) {
        return CommonResult.ok(auditLogService.page(action, pageNo, pageSize));
    }
}
