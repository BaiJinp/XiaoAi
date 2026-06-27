package com.xiaoai.agent.runtime.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RunStartCommand {

    private final Long tenantId;

    private final Long userId;

    private final Long agentId;

    private final Long agentVersionId;

    private final Long taskId;

    private final Long runId;

    private final String channelType;

    private final String inputText;

    private final String traceId;

    private final String runtimeSnapshotJson;
}
