package com.xiaoai.agent.policy.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PolicyDecisionResponse {

    private final String effect;

    private final Long matchedRuleId;

    private final String matchedRuleCode;

    private final List<Long> approverUserIds;

    private final String reason;
}
