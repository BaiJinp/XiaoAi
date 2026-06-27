package com.xiaoai.agent.approval.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateApprovalRequestCommand {

    @NotNull
    private Long taskId;

    @NotNull
    private Long runId;

    private Long stepId;

    @NotNull
    private Long applicantUserId;

    @NotNull
    private Long approverUserId;

    @NotBlank
    private String approvalType;

    private String reason;

    private String requestPayloadJson;
}
