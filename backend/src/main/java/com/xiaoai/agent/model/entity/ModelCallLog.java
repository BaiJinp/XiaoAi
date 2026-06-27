package com.xiaoai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@TableName("model_call_log")
public class ModelCallLog extends TenantEntity {
    private Long taskId;

    private Long runId;

    private Long stepId;

    private Long providerId;

    private Long modelId;

    private String callType;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private BigDecimal costAmount;

    private String status;

    private String errorMessage;

    private Integer latencyMs;

    private String traceId;

    private String requestSummary;

    private String responseSummary;
}
