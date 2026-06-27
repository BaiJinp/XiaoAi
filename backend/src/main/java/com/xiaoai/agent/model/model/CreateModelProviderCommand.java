package com.xiaoai.agent.model.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateModelProviderCommand {

    @NotBlank
    private String providerCode;

    @NotBlank
    private String providerName;

    @NotBlank
    private String providerType;

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String apiKey;
}
