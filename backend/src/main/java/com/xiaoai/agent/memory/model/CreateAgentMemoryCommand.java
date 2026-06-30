package com.xiaoai.agent.memory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAgentMemoryCommand {

    private Long tenantId;

    @NotNull
    private Long agentId;

    private Long agentVersionId;

    private Long taskId;

    private Long runId;

    private Long sessionId;

    private Long userId;

    private String memoryType;

    private String memoryScope;

    @NotBlank
    private String summaryText;

    private String sourceText;

    private String confidence;

    private String policyJson;
}
