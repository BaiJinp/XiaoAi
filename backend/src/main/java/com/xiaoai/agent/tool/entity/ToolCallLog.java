package com.xiaoai.agent.tool.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("tool_call_log")
public class ToolCallLog extends TenantEntity {
    private Long taskId;

    private Long runId;

    private Long stepId;

    private Long toolId;

    private String riskLevel;

    private String callStatus;

    private String requestSummary;

    private String responseSummary;

    private String errorMessage;

    private Integer latencyMs;

    private Long approvalRequestId;

    private String traceId;
}
