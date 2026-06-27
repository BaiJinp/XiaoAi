package com.xiaoai.agent.collaboration.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateAgentThreadCommand {

    @NotNull
    private Long sessionId;

    private Long parentThreadId;

    @NotNull
    private Long agentId;

    private Long agentVersionId;

    private Long roleId;

    private String threadName;

    private Long inputArtifactId;

    private List<Long> inputArtifactIds;

    private List<String> outputArtifactTypes;

    private Long inputArtifactVersion;

    private String stageCode;

    private boolean createTask;

    private String inputText;

    private String toolCallsJson;

    private boolean requireAcceptedInputHandoff;
}
