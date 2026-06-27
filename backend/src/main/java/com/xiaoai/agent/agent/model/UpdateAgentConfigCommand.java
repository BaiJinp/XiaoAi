package com.xiaoai.agent.agent.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateAgentConfigCommand {

    private String rolePrompt;

    private String responsibilityText;

    private String boundaryText;

    private String configJson;

    private String knowledgeScopeJson;

    private String toolScopeJson;

    private List<Long> toolIds;

    private String modelPolicyJson;

    private String toolPolicyJson;

    private String contextPolicyJson;

    private String memoryPolicyJson;

    private String orchestrationPolicyJson;

    private String permissionPolicyJson;

    private String budgetPolicyJson;
}
