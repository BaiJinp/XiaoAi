package com.xiaoai.agent.plugin.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportPluginManifestCommand {

    @NotBlank
    private String manifestJson;

    @Positive
    private Long agentId;

    @Positive
    private Long agentVersionId;
}
