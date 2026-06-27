package com.xiaoai.agent.approval.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApprovalRequestResponse {

    private final Long approvalRequestId;

    private final String requestCode;

    private final String approvalStatus;
}
