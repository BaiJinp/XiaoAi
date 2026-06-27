package com.xiaoai.agent.knowledge.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.knowledge.entity.KnowledgeDocument;
import com.xiaoai.agent.knowledge.model.IngestKnowledgeTextCommand;
import com.xiaoai.agent.knowledge.model.KnowledgeIngestResponse;
import com.xiaoai.agent.knowledge.model.KnowledgeRetrieveResult;
import com.xiaoai.agent.knowledge.model.RetrieveKnowledgeCommand;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/knowledge-documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping("/{id}")
public ApiResponse<KnowledgeDocument> getById(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDocumentService.getDocument(id));
    }

    @PostMapping("/ingest-text")
public ApiResponse<KnowledgeIngestResponse> ingestText(@Valid @RequestBody IngestKnowledgeTextCommand command) {
        return ApiResponse.success(knowledgeDocumentService.ingestText(command));
    }

    @PostMapping("/retrieve")
    public ApiResponse<List<KnowledgeRetrieveResult>> retrieve(@Valid @RequestBody RetrieveKnowledgeCommand command) {
        return ApiResponse.success(knowledgeDocumentService.retrieve(command));
    }
}
