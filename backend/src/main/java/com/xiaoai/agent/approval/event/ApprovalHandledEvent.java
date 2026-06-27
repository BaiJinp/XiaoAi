package com.xiaoai.agent.approval.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApprovalHandledEvent {

    private final Long tenantId;

    private final Long approvalRequestId;

    private final Long taskId;

    private final Long runId;

    private final Long operatorUserId;

    private final String action;

    private final String commentText;
}
