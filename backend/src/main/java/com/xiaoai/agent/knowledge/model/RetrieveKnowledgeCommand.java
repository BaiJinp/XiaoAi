package com.xiaoai.agent.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RetrieveKnowledgeCommand {

    @NotNull
    private Long knowledgeBaseId;

    @NotBlank
    private String query;

    private Integer topK = 5;
}
