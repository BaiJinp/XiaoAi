package com.xiaoai.agent.collaboration.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollaborationSessionResponse {

    private final Long sessionId;

    private final String sessionCode;

    private final String status;
}
