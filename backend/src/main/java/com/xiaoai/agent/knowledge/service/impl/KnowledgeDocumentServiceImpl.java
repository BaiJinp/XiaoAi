package com.xiaoai.agent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.knowledge.entity.KnowledgeChunk;
import com.xiaoai.agent.knowledge.entity.KnowledgeDocument;
import com.xiaoai.agent.knowledge.mapper.KnowledgeDocumentMapper;
import com.xiaoai.agent.knowledge.model.IngestKnowledgeTextCommand;
import com.xiaoai.agent.knowledge.model.KnowledgeIngestResponse;
import com.xiaoai.agent.knowledge.model.KnowledgeRetrieveResult;
import com.xiaoai.agent.knowledge.model.RetrieveKnowledgeCommand;
import com.xiaoai.agent.knowledge.service.KnowledgeChunkService;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> implements KnowledgeDocumentService {

    private static final int CHUNK_SIZE = 500;

    private final KnowledgeChunkService knowledgeChunkService;

    public KnowledgeDocumentServiceImpl(KnowledgeChunkService knowledgeChunkService) {
        this.knowledgeChunkService = knowledgeChunkService;
    }

    @Override
public KnowledgeDocument getDocument(Long documentId) {
        Long tenantId = UserContextHolder.requireTenantId();
        KnowledgeDocument document = getBaseMapper().selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getId, documentId)
                .last("limit 1"));
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Knowledge document not found");
        }
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public KnowledgeIngestResponse ingestText(IngestKnowledgeTextCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(command.getKnowledgeBaseId());
        document.setDocumentCode("DOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        document.setDocumentName(command.getDocumentName());
        document.setFileType("text");
        document.setStorageUrl("inline");
        document.setParseStatus("parsed");
        document.setMetadataJson(command.getMetadataJson() == null ? "{}" : command.getMetadataJson());
        save(document);

        List<KnowledgeChunk> chunks = splitText(command.getText(), command.getKnowledgeBaseId(), document.getId());
        knowledgeChunkService.saveBatch(chunks);
        return KnowledgeIngestResponse.builder()
                .documentId(document.getId())
                .documentCode(document.getDocumentCode())
                .chunkCount(chunks.size())
                .build();
    }

    @Override
public List<KnowledgeRetrieveResult> retrieve(RetrieveKnowledgeCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        int topK = command.getTopK() == null || command.getTopK() <= 0 ? 5 : Math.min(command.getTopK(), 20);
        List<String> terms = queryTerms(command.getQuery());
        List<KnowledgeChunk> chunks = knowledgeChunkService.list(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getTenantId, tenantId)
                .eq(KnowledgeChunk::getKnowledgeBaseId, command.getKnowledgeBaseId()));
        return chunks.stream()
                .map(chunk -> toResult(chunk, terms))
                .filter(result -> result.getScore() > 0)
                .sorted(Comparator.comparing(KnowledgeRetrieveResult::getScore).reversed()
                        .thenComparing(KnowledgeRetrieveResult::getChunkId))
                .limit(topK)
                .toList();
    }

    private List<KnowledgeChunk> splitText(String text, Long knowledgeBaseId, Long documentId) {
        Long tenantId = UserContextHolder.requireTenantId();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int index = 0;
        for (int start = 0; start < text.length(); start += CHUNK_SIZE) {
            String chunkText = text.substring(start, Math.min(start + CHUNK_SIZE, text.length()));
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setTenantId(tenantId);
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(index++);
            chunk.setChunkText(chunkText);
            chunk.setTokenCount(estimateTokens(chunkText));
            chunk.setSourceJson("{\"documentId\":" + documentId + ",\"chunkIndex\":" + chunk.getChunkIndex() + "}");
            chunk.setMetadataJson("{}");
            chunks.add(chunk);
        }
        return chunks;
    }

    private KnowledgeRetrieveResult toResult(KnowledgeChunk chunk, List<String> terms) {
        String text = chunk.getChunkText() == null ? "" : chunk.getChunkText().toLowerCase();
        int score = 0;
        for (String term : terms) {
            if (text.contains(term.toLowerCase())) {
                score++;
            }
        }
        return KnowledgeRetrieveResult.builder()
                .chunkId(chunk.getId())
                .documentId(chunk.getDocumentId())
                .chunkIndex(chunk.getChunkIndex())
                .chunkText(chunk.getChunkText())
                .sourceJson(chunk.getSourceJson())
                .score(score)
                .sourceTitle("Document #" + chunk.getDocumentId() + " / chunk " + chunk.getChunkIndex())
                .sourceType("knowledge_base")
                .snippet(snippet(chunk.getChunkText()))
                .confidence(confidence(score, terms.size()))
                .accessChecked(true)
                .build();
    }

    private String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.strip();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String confidence(int score, int termCount) {
        if (score <= 0) {
            return "insufficient";
        }
        if (termCount <= 1) {
            return "high";
        }
        if (score >= termCount) {
            return "high";
        }
        if (score * 2 >= termCount) {
            return "medium";
        }
        return "low";
    }

    private List<String> queryTerms(String query) {
        String normalized = query.trim();
        if (normalized.contains(" ")) {
            return List.of(normalized.split("\\s+"));
        }
        return List.of(normalized);
    }

    private Integer estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0D));
    }
}
