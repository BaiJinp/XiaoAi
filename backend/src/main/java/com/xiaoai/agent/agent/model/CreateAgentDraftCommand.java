package com.xiaoai.agent.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAgentDraftCommand {

    @NotBlank
    private String agentName;

    private String agentType;

    private String description;

    private String rolePrompt;

    private String responsibilityText;

    private String boundaryText;

    private String capabilityJson;

    private String toolPolicyJson;

    private String contextPolicyJson;

    private String memoryPolicyJson;

    private String orchestrationPolicyJson;

    @NotNull
    private Long ownerUserId;

    private Long modelProviderId;

    private Long modelConfigId;
}
