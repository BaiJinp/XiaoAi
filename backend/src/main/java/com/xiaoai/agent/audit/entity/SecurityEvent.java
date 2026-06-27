package com.xiaoai.agent.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("security_event")
public class SecurityEvent extends TenantEntity {
    private Long userId;

    private Long agentId;

    private Long taskId;

    private Long runId;

    private String eventType;

    private String riskLevel;

    private String eventStatus;

    private String summary;

    private String detailJson;
}
