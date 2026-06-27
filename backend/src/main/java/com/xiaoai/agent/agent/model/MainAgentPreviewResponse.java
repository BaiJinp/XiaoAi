package com.xiaoai.agent.agent.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MainAgentPreviewResponse {

    private final String agentName;

    private final String agentType;

    private final String description;

    private final String rolePrompt;

    private final String responsibilityText;

    private final String boundaryText;

    private final List<String> capabilities;

    private final List<String> sampleTasks;

    private final List<String> governanceRules;
}
