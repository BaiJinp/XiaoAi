package com.xiaoai.agent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
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
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentServiceImplTest {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkService knowledgeChunkService = mock(KnowledgeChunkService.class);
    private final KnowledgeDocumentServiceImpl knowledgeDocumentService = new KnowledgeDocumentServiceImpl(knowledgeChunkService);

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(knowledgeDocumentService, knowledgeDocumentMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-knowledge")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void ingestTextShouldCreateDocumentAndChunks() {
        when(knowledgeDocumentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(10L);
            return 1;
        });
        when(knowledgeChunkService.saveBatch(any())).thenReturn(true);
        IngestKnowledgeTextCommand command = new IngestKnowledgeTextCommand();
        command.setKnowledgeBaseId(1L);
        command.setDocumentName("项目资料");
        command.setText("项目风险需要每周跟踪，延期事项需要升级。");

        KnowledgeIngestResponse response = knowledgeDocumentService.ingestText(command);

        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper).insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getParseStatus()).isEqualTo("parsed");
        ArgumentCaptor<List<KnowledgeChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeChunkService).saveBatch(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(1);
        assertThat(chunksCaptor.getValue().get(0).getTenantId()).isEqualTo(100L);
        assertThat(chunksCaptor.getValue().get(0).getSourceJson()).contains("\"documentId\":10");
        assertThat(response.getDocumentId()).isEqualTo(10L);
        assertThat(response.getChunkCount()).isEqualTo(1);
    }

    @Test
    void getDocumentShouldReturnTenantScopedDocument() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(10L);
        document.setTenantId(100L);
        document.setDocumentName("项目资料");
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(document);

        KnowledgeDocument result = knowledgeDocumentService.getDocument(10L);

        assertThat(result.getDocumentName()).isEqualTo("项目资料");
    }

    @Test
    void getDocumentShouldRejectMissingOrCrossTenantDocument() {
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> knowledgeDocumentService.getDocument(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Knowledge document not found");
    }

    @Test
    void retrieveShouldReturnMatchedChunksOrderedByScore() {
        KnowledgeChunk first = chunk(1L, "项目风险需要每周跟踪，风险需要负责人确认。");
        KnowledgeChunk second = chunk(2L, "会议纪要包含行动项。");
        when(knowledgeChunkService.list(any(Wrapper.class))).thenReturn(List.of(second, first));
        RetrieveKnowledgeCommand command = new RetrieveKnowledgeCommand();
        command.setKnowledgeBaseId(1L);
        command.setQuery("风险");

        List<KnowledgeRetrieveResult> results = knowledgeDocumentService.retrieve(command);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo(1L);
        assertThat(results.get(0).getScore()).isEqualTo(1);
        assertThat(results.get(0).getSourceJson()).contains("\"chunkIndex\":0");
        assertThat(results.get(0).getSourceType()).isEqualTo("knowledge_base");
        assertThat(results.get(0).getSourceTitle()).isEqualTo("Document #10 / chunk 0");
        assertThat(results.get(0).getConfidence()).isEqualTo("high");
        assertThat(results.get(0).getAccessChecked()).isTrue();
    }

    @Test
    void ingestTextShouldSplitLongTextIntoIndexedChunks() {
        when(knowledgeDocumentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(11L);
            return 1;
        });
        when(knowledgeChunkService.saveBatch(any())).thenReturn(true);
        IngestKnowledgeTextCommand command = new IngestKnowledgeTextCommand();
        command.setKnowledgeBaseId(1L);
        command.setDocumentName("长文档");
        command.setText("A".repeat(1200));
        command.setMetadataJson("{\"source\":\"meeting\"}");

        KnowledgeIngestResponse response = knowledgeDocumentService.ingestText(command);

        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper).insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getMetadataJson()).isEqualTo("{\"source\":\"meeting\"}");
        ArgumentCaptor<List<KnowledgeChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeChunkService).saveBatch(chunksCaptor.capture());
        List<KnowledgeChunk> chunks = chunksCaptor.getValue();
        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(KnowledgeChunk::getChunkIndex).containsExactly(0, 1, 2);
        assertThat(chunks).extracting(KnowledgeChunk::getTokenCount).containsExactly(125, 125, 50);
        assertThat(chunks.get(2).getChunkText()).hasSize(200);
        assertThat(response.getChunkCount()).isEqualTo(3);
    }

    @Test
    void ingestTextShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .traceId("trace-knowledge")
                .build());
        IngestKnowledgeTextCommand command = new IngestKnowledgeTextCommand();
        command.setKnowledgeBaseId(1L);
        command.setDocumentName("项目资料");
        command.setText("项目风险需要跟踪");

        assertThatThrownBy(() -> knowledgeDocumentService.ingestText(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");
    }

    @Test
    void retrieveShouldUseDefaultTopKAndOrderScoreThenChunkId() {
        when(knowledgeChunkService.list(any(Wrapper.class))).thenReturn(List.of(
                chunk(7L, "风险 延期"),
                chunk(3L, "风险 延期"),
                chunk(4L, "风险"),
                chunk(5L, "风险"),
                chunk(6L, "风险"),
                chunk(8L, "风险"),
                chunk(9L, "无关内容")
        ));
        RetrieveKnowledgeCommand command = new RetrieveKnowledgeCommand();
        command.setKnowledgeBaseId(1L);
        command.setQuery("风险 延期");
        command.setTopK(0);

        List<KnowledgeRetrieveResult> results = knowledgeDocumentService.retrieve(command);

        assertThat(results).extracting(KnowledgeRetrieveResult::getChunkId)
                .containsExactly(3L, 7L, 4L, 5L, 6L);
        assertThat(results).extracting(KnowledgeRetrieveResult::getScore)
                .containsExactly(2, 2, 1, 1, 1);
        assertThat(results).extracting(KnowledgeRetrieveResult::getConfidence)
                .containsExactly("high", "high", "medium", "medium", "medium");
    }

    private KnowledgeChunk chunk(Long id, String text) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setKnowledgeBaseId(1L);
        chunk.setDocumentId(10L);
        chunk.setChunkIndex(0);
        chunk.setChunkText(text);
        chunk.setSourceJson("{\"documentId\":10,\"chunkIndex\":0}");
        return chunk;
    }
}
