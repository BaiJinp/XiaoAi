package com.xiaoai.agent.runtime.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RuntimeEvent {

    private final Long tenantId;

    private final Long userId;

    private final Long agentId;

    private final Long taskId;

    private final Long runId;

    private final Long stepId;

    private final String traceId;

    private final String eventType;

    private final String eventSummary;

    private final String payloadJson;

    private final OffsetDateTime occurredAt;
}
