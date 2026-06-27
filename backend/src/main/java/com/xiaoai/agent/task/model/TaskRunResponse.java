package com.xiaoai.agent.task.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskRunResponse {

    private final Long taskId;

    private final Long runId;

    private final String runCode;

    private final String status;

    private final String runtimeType;
}
