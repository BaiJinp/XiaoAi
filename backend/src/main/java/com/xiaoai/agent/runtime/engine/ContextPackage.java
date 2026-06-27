package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContextPackage {

    private final Long tenantId;

    private final Long userId;

    private final Long agentId;

    private final Long agentVersionId;

    private final Long taskId;

    private final Long runId;

    private final String channelType;

    private final String traceId;

    private final String inputText;

    private final String runtimeSnapshotJson;

    private final JsonNode inputRoot;

    private final JsonNode runtimeSnapshotRoot;

    private final ExecutionMode executionMode;

    public static ContextPackage from(RunStartCommand command,
                                      JsonNode inputRoot,
                                      JsonNode runtimeSnapshotRoot,
                                      ExecutionMode executionMode) {
        return from(command, command.getInputText(), inputRoot, runtimeSnapshotRoot, executionMode);
    }

    public static ContextPackage from(RunStartCommand command,
                                      String inputText,
                                      JsonNode inputRoot,
                                      JsonNode runtimeSnapshotRoot,
                                      ExecutionMode executionMode) {
        return ContextPackage.builder()
                .tenantId(command.getTenantId())
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .agentVersionId(command.getAgentVersionId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .channelType(command.getChannelType())
                .traceId(command.getTraceId())
                .inputText(inputText)
                .runtimeSnapshotJson(command.getRuntimeSnapshotJson())
                .inputRoot(inputRoot)
                .runtimeSnapshotRoot(runtimeSnapshotRoot)
                .executionMode(executionMode)
                .build();
    }
}
