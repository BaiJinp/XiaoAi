package com.xiaoai.agent.agent.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MainAgentSetupResponse {

    private final Long agentId;

    private final String agentCode;

    private final Long agentVersionId;

    private final String versionNo;

    private final String status;

    private final MainAgentPreviewResponse preview;
}
