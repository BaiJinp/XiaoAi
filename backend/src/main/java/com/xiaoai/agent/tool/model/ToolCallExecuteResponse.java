package com.xiaoai.agent.tool.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ToolCallExecuteResponse {

    private final String status;

    private final Long toolCallLogId;

    private final Long approvalRequestId;

    private final String toolType;

    private final String toolCode;

    private final String riskLevel;

    private final String executorType;

    private final String resultJson;

    private final String errorMessage;
}
