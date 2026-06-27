package com.xiaoai.agent.collaboration.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateQualityGateCommand {

    @NotNull
    private Long sessionId;

    @NotBlank
    private String gateCode;

    @NotBlank
    private String gateName;

    @NotBlank
    private String gateType;

    private Boolean required;

    private String ruleJson;
}
