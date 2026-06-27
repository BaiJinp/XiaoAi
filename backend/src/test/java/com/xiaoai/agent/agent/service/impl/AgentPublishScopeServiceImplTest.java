package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.entity.AgentPublishScope;
import com.xiaoai.agent.agent.mapper.AgentPublishScopeMapper;
import com.xiaoai.agent.agent.model.PublishAgentCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPublishScopeServiceImplTest {

    private final AgentPublishScopeMapper mapper = mock(AgentPublishScopeMapper.class);
    private final AgentService agentService = mock(AgentService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final AgentPublishScopeServiceImpl publishScopeService = new AgentPublishScopeServiceImpl(agentService, agentVersionService);

    AgentPublishScopeServiceImplTest() {
        TestReflectionUtils.injectBaseMapper(publishScopeService, mapper);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void publishShouldCreateScopeAndUpdateAgentVersionStatus() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(mapper.insert(any(AgentPublishScope.class))).thenReturn(1);
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setTenantId(100L);
        agent.setStatus("draft");
        when(agentService.getAgent(1L)).thenReturn(agent);
        PublishAgentCommand command = new PublishAgentCommand();
        command.setAgentVersionId(9L);
        command.setScopeType("user");
        command.setScopeValue("200");

        publishScopeService.publish(1L, command);

        verify(agentVersionService).publishVersion(1L, 9L);
        ArgumentCaptor<AgentPublishScope> scopeCaptor = ArgumentCaptor.forClass(AgentPublishScope.class);
        verify(mapper).insert(scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().getTenantId()).isEqualTo(100L);
        assertThat(scopeCaptor.getValue().getStatus()).isEqualTo("active");
        ArgumentCaptor<Agent> agentCaptor = ArgumentCaptor.forClass(Agent.class);
        verify(agentService).updateById(agentCaptor.capture());
        assertThat(agentCaptor.getValue().getStatus()).isEqualTo("published");
        assertThat(agentCaptor.getValue().getCurrentVersionId()).isEqualTo(9L);
        assertThat(agentCaptor.getValue().getLatestStableVersionId()).isEqualTo(9L);
    }

    @Test
    void publishShouldRejectCrossTenantAgent() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setTenantId(999L);
        when(agentService.getAgent(1L)).thenThrow(new BusinessException(com.xiaoai.agent.common.api.ErrorCode.NOT_FOUND, "Agent not found"));
        PublishAgentCommand command = new PublishAgentCommand();
        command.setAgentVersionId(9L);
        command.setScopeType("user");
        command.setScopeValue("200");

        assertThatThrownBy(() -> publishScopeService.publish(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent not found");

        verify(mapper, never()).insert(any(AgentPublishScope.class));
        verify(agentService, never()).updateById(any(Agent.class));
    }

    @Test
    void publishShouldStopWhenVersionDoesNotBelongToAgent() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setTenantId(100L);
        when(agentService.getAgent(1L)).thenReturn(agent);
        when(agentVersionService.publishVersion(1L, 9L))
                .thenThrow(new BusinessException(com.xiaoai.agent.common.api.ErrorCode.BAD_REQUEST,
                        "Agent version does not belong to agent"));
        PublishAgentCommand command = new PublishAgentCommand();
        command.setAgentVersionId(9L);
        command.setScopeType("user");
        command.setScopeValue("200");

        assertThatThrownBy(() -> publishScopeService.publish(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent version does not belong to agent");

        verify(mapper, never()).insert(any(AgentPublishScope.class));
        verify(agentService, never()).updateById(any(Agent.class));
    }

    @Test
    void getPublishScopeShouldReturnTenantScopedScope() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        AgentPublishScope scope = new AgentPublishScope();
        scope.setId(1L);
        scope.setTenantId(100L);
        scope.setStatus("active");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(scope);

        AgentPublishScope result = publishScopeService.getPublishScope(1L);

        assertThat(result.getStatus()).isEqualTo("active");
    }

    @Test
    void getPublishScopeShouldRejectMissingOrCrossTenantScope() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> publishScopeService.getPublishScope(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent publish scope not found");
    }
}
