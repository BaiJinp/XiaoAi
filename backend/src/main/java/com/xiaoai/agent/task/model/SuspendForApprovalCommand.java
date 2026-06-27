package com.xiaoai.agent.task.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuspendForApprovalCommand {

    @NotNull
    private Long approvalRequestId;

    private String reason;
}
