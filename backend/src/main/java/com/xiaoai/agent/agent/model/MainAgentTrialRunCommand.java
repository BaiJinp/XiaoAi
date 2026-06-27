package com.xiaoai.agent.agent.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MainAgentTrialRunCommand {

    @NotNull
    private Long agentId;

    private Long agentVersionId;

    @NotNull
    private Long userId;

    private String assistantTaskType;

    private String prompt;

    private String query;
}
