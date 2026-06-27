package com.xiaoai.agent.knowledge.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KnowledgeRetrieveResult {

    private final Long chunkId;

    private final Long documentId;

    private final Integer chunkIndex;

    private final String chunkText;

    private final String sourceJson;

    private final Integer score;

    private final String sourceTitle;

    private final String sourceType;

    private final String snippet;

    private final String confidence;

    private final Boolean accessChecked;
}
