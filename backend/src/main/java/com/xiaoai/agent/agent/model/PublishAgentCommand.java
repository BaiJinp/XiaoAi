package com.xiaoai.agent.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishAgentCommand {

    @NotNull
    private Long agentVersionId;

    @NotBlank
    private String scopeType;

    @NotBlank
    private String scopeValue;
}
