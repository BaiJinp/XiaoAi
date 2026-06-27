package com.xiaoai.agent.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.knowledge.entity.KnowledgeDocument;
import com.xiaoai.agent.knowledge.model.IngestKnowledgeTextCommand;
import com.xiaoai.agent.knowledge.model.KnowledgeIngestResponse;
import com.xiaoai.agent.knowledge.model.KnowledgeRetrieveResult;
import com.xiaoai.agent.knowledge.model.RetrieveKnowledgeCommand;

import java.util.List;

public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    KnowledgeDocument getDocument(Long documentId);

    KnowledgeIngestResponse ingestText(IngestKnowledgeTextCommand command);

    List<KnowledgeRetrieveResult> retrieve(RetrieveKnowledgeCommand command);
}
