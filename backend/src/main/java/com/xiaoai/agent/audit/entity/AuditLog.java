package com.xiaoai.agent.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("audit_log")
public class AuditLog extends TenantEntity {
    private Long userId;

    private Long agentId;

    private Long taskId;

    private Long runId;

    private String auditType;

    private String action;

    private String resourceType;

    private String resourceId;

    private String riskLevel;

    private String summary;

    private String detailJson;

    private String traceId;
}
