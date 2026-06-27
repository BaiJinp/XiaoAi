package com.xiaoai.agent.knowledge.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KnowledgeIngestResponse {

    private final Long documentId;

    private final String documentCode;

    private final Integer chunkCount;
}
