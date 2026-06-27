package com.xiaoai.agent.tool.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EvaluateToolCallCommand {

    @NotNull
    private Long toolId;

    @NotNull
    private Long taskId;

    @NotNull
    private Long runId;

    @NotNull
    private Long applicantUserId;

    private Long fallbackApproverUserId;

    private Long agentVersionId;

    private String callPayloadJson;
}
