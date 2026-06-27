package com.xiaoai.agent.task.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskCreateResponse {

    private final Long taskId;

    private final String taskCode;

    private final String status;
}
