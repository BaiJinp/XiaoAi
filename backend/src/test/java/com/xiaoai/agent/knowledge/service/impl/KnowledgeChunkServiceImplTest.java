package com.xiaoai.agent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.knowledge.entity.KnowledgeChunk;
import com.xiaoai.agent.knowledge.mapper.KnowledgeChunkMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeChunkServiceImplTest {

    private final KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
    private final KnowledgeChunkServiceImpl knowledgeChunkService = new KnowledgeChunkServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(knowledgeChunkService, knowledgeChunkMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getChunkShouldReturnTenantScopedChunk() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(1L);
        chunk.setTenantId(100L);
        chunk.setChunkText("project risk");
        when(knowledgeChunkMapper.selectOne(any(Wrapper.class))).thenReturn(chunk);

        KnowledgeChunk result = knowledgeChunkService.getChunk(1L);

        assertThat(result.getChunkText()).isEqualTo("project risk");
    }

    @Test
    void getChunkShouldRejectMissingOrCrossTenantChunk() {
        when(knowledgeChunkMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> knowledgeChunkService.getChunk(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Knowledge chunk not found");
    }
}
