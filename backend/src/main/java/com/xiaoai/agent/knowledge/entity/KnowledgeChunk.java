package com.xiaoai.agent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("knowledge_chunk")
public class KnowledgeChunk extends TenantEntity {
    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String chunkText;

    private String embeddingRef;

    private Integer tokenCount;

    private String sourceJson;

    private String metadataJson;
}
