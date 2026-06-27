package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.AgentKnowledgeBinding;
import com.xiaoai.agent.agent.mapper.AgentKnowledgeBindingMapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKnowledgeBindingServiceImplTest {

    private final AgentKnowledgeBindingMapper mapper = mock(AgentKnowledgeBindingMapper.class);
    private final AgentKnowledgeBindingServiceImpl service = new AgentKnowledgeBindingServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(service, mapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getBindingShouldReturnTenantScopedBinding() {
        AgentKnowledgeBinding binding = new AgentKnowledgeBinding();
        binding.setId(1L);
        binding.setTenantId(100L);
        binding.setBindingStatus("active");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(binding);

        AgentKnowledgeBinding result = service.getBinding(1L);

        assertThat(result.getBindingStatus()).isEqualTo("active");
    }

    @Test
    void getBindingShouldRejectMissingOrCrossTenantBinding() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getBinding(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent knowledge binding not found");
    }
}
