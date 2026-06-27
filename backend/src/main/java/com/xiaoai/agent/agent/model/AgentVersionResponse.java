package com.xiaoai.agent.agent.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AgentVersionResponse {

    private final Long agentVersionId;

    private final String versionNo;

    private final String versionStatus;

    private final String runtimeSnapshotJson;

    private final String modelPolicyJson;

    private final String toolPolicyJson;

    private final String contextPolicyJson;

    private final String memoryPolicyJson;

    private final String orchestrationPolicyJson;

    private final List<Long> toolIds;
}
