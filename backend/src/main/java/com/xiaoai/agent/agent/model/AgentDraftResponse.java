package com.xiaoai.agent.agent.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentDraftResponse {

    private final Long agentId;

    private final String agentCode;

    private final String status;
}
