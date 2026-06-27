package com.xiaoai.agent.collaboration.strategy;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollaborationStrategyContext {

    private final Long sessionId;

    private final Long planId;

    private final String gateCode;
}
