package com.xiaoai.agent.model.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmbeddingModelCommand {

    @NotNull
    private Long modelId;

    private Long taskId;

    private Long runId;

    private Long stepId;

    @NotBlank
    private String input;
}
