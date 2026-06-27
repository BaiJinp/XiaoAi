package com.xiaoai.agent.collaboration.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentThreadResponse {

    private final Long threadId;

    private final String threadCode;

    private final Long taskId;

    private final String status;
}
