package com.xiaoai.agent.task.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskStatusChangedEvent {

    private final Long tenantId;

    private final Long userId;

    private final Long taskId;

    private final Long runId;

    private final String status;

    private final String source;
}
