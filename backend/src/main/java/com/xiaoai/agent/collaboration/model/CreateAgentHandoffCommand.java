package com.xiaoai.agent.collaboration.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAgentHandoffCommand {

    @NotNull
    private Long sessionId;

    private Long fromThreadId;

    private Long toThreadId;

    private Long artifactId;

    private String handoffType;

    private String messageText;
}
