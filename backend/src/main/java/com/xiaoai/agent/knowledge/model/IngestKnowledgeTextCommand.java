package com.xiaoai.agent.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IngestKnowledgeTextCommand {

    @NotNull
    private Long knowledgeBaseId;

    @NotBlank
    private String documentName;

    @NotBlank
    private String text;

    private String metadataJson;
}
