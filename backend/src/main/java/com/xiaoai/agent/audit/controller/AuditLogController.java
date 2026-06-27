package com.xiaoai.agent.audit.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.audit.entity.AuditLog;
import com.xiaoai.agent.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/{id}")
public ApiResponse<AuditLog> getById(@PathVariable Long id) {
        return ApiResponse.success(auditLogService.getById(id));
    }
}
