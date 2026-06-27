package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.mapper.AgentToolBindingMapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolBindingServiceImplTest {

    private final AgentToolBindingMapper mapper = mock(AgentToolBindingMapper.class);
    private final AgentToolBindingServiceImpl service = new AgentToolBindingServiceImpl();

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
        AgentToolBinding binding = new AgentToolBinding();
        binding.setId(1L);
        binding.setTenantId(100L);
        binding.setBindingStatus("active");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(binding);

        AgentToolBinding result = service.getBinding(1L);

        assertThat(result.getBindingStatus()).isEqualTo("active");
    }

    @Test
    void getBindingShouldRejectMissingOrCrossTenantBinding() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getBinding(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent tool binding not found");
    }

    @Test
    void listVersionBindingsShouldReturnTenantScopedBindings() {
        AgentToolBinding binding = new AgentToolBinding();
        binding.setId(1L);
        binding.setTenantId(100L);
        binding.setAgentVersionId(10L);
        binding.setBindingStatus("active");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));

        List<AgentToolBinding> result = service.listVersionBindings(10L);

        assertThat(result).containsExactly(binding);
        verify(mapper).selectList(any(Wrapper.class));
    }
}
