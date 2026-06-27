package com.xiaoai.agent.collaboration.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCollaborationSessionCommand {

    private Long templateId;

    private Long rootTaskId;

    @NotBlank
    private String strategyType;

    @NotBlank
    private String goalText;

    private String contextJson;
}
