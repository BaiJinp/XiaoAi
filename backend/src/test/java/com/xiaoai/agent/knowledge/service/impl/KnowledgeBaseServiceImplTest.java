package com.xiaoai.agent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.knowledge.entity.KnowledgeBase;
import com.xiaoai.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceImplTest {

    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeBaseServiceImpl knowledgeBaseService = new KnowledgeBaseServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(knowledgeBaseService, knowledgeBaseMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getKnowledgeBaseShouldReturnTenantScopedKnowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        knowledgeBase.setTenantId(100L);
        knowledgeBase.setKbName("project docs");
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(knowledgeBase);

        KnowledgeBase result = knowledgeBaseService.getKnowledgeBase(1L);

        assertThat(result.getKbName()).isEqualTo("project docs");
    }

    @Test
    void getKnowledgeBaseShouldRejectMissingOrCrossTenantKnowledgeBase() {
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> knowledgeBaseService.getKnowledgeBase(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Knowledge base not found");
    }
}
