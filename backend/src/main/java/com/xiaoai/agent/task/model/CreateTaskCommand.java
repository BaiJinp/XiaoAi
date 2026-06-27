package com.xiaoai.agent.task.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTaskCommand {

    @NotNull
    private Long agentId;

    private Long agentVersionId;

    @NotNull
    private Long userId;

    private String channelType;

    private String title;

    @NotBlank
    private String inputText;
}
