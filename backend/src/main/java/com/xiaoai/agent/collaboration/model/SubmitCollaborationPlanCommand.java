package com.xiaoai.agent.collaboration.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitCollaborationPlanCommand {

    private Long sessionId;

    private Long generatedByThreadId;

    @NotBlank
    private String planJson;
}
