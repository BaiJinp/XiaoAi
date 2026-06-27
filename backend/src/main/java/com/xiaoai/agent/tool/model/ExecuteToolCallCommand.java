package com.xiaoai.agent.tool.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecuteToolCallCommand extends EvaluateToolCallCommand {

    @NotNull
    private Boolean dryRun = false;

    private Boolean approvalBypassed = false;

    private Long approvalRequestId;
}
