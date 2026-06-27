package com.xiaoai.agent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("knowledge_document")
public class KnowledgeDocument extends TenantEntity {
    private Long knowledgeBaseId;

    private String documentCode;

    private String documentName;

    private String fileType;

    private String storageUrl;

    private String parseStatus;

    private String parseError;

    private String metadataJson;
}
