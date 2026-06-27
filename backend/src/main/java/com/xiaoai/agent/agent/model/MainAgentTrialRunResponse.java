package com.xiaoai.agent.agent.model;

import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.entity.TaskArtifact;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MainAgentTrialRunResponse {

    private final Long taskId;

    private final String taskCode;

    private final Long runId;

    private final String runCode;

    private final String status;

    private final String taskStatus;

    private final String runtimeType;

    private final String resultSummary;

    private final List<TaskEvent> events;

    private final List<TaskArtifact> artifacts;
}
