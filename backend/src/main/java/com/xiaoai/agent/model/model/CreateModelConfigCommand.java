package com.xiaoai.agent.model.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateModelConfigCommand {

    @NotNull
    private Long providerId;

    @NotBlank
    private String modelCode;

    @NotBlank
    private String modelName;

    @NotBlank
    private String modelType;

    private Integer contextWindow;

    private String configJson;
}
