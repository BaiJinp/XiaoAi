package com.xiaoai.agent.tool.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ToolCallDecisionResponse {

    private final String decision;

    private final String riskLevel;

    private final Long approvalRequestId;

    private final String approvalStatus;

    private final List<Long> approverUserIds;

    private final String reason;
}
