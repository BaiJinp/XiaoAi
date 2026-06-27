package com.xiaoai.agent.runtime.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RunApprovalResultCommand {

    private final Long tenantId;

    private final Long taskId;

    private final Long runId;

    private final Long approvalRequestId;

    private final Long approverUserId;

    private final String approvalStatus;

    private final String commentText;
}
