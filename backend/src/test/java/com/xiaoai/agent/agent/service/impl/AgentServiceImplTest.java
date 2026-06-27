package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.mapper.AgentMapper;
import com.xiaoai.agent.agent.model.AgentDraftResponse;
import com.xiaoai.agent.agent.model.CreateAgentDraftCommand;
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

class AgentServiceImplTest {

    private final AgentMapper agentMapper = mock(AgentMapper.class);
    private final AgentServiceImpl agentService = new AgentServiceImpl();

    AgentServiceImplTest() {
        TestReflectionUtils.injectBaseMapper(agentService, agentMapper);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createDraftShouldFillProjectAssistantDefaults() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            agent.setId(1L);
            return 1;
        });
        CreateAgentDraftCommand command = new CreateAgentDraftCommand();
        command.setAgentName("项目助理");
        command.setDescription("处理项目任务");
        command.setAgentType("team_orchestrator");
        command.setRolePrompt("Coordinate a team of specialist agents.");
        command.setResponsibilityText("Plan, assign, and summarize delivery.");
        command.setBoundaryText("High-risk tool calls require approval.");
        command.setCapabilityJson("[{\"code\":\"team_orchestration\"}]");
        command.setToolPolicyJson("{\"write\":\"approve\"}");
        command.setContextPolicyJson("{\"session\":\"summary\"}");
        command.setMemoryPolicyJson("{\"longTerm\":\"disabled\"}");
        command.setOrchestrationPolicyJson("{\"executionMode\":\"multi_agent\"}");
        command.setOwnerUserId(300L);
        command.setModelProviderId(10L);
        command.setModelConfigId(11L);

        AgentDraftResponse response = agentService.createDraft(command);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentMapper).insert(captor.capture());
        Agent saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getAgentType()).isEqualTo("team_orchestrator");
        assertThat(saved.getRolePrompt()).isEqualTo("Coordinate a team of specialist agents.");
        assertThat(saved.getResponsibilityText()).isEqualTo("Plan, assign, and summarize delivery.");
        assertThat(saved.getBoundaryText()).isEqualTo("High-risk tool calls require approval.");
        assertThat(saved.getCapabilityJson()).contains("team_orchestration");
        assertThat(saved.getToolPolicyJson()).contains("\"write\":\"approve\"");
        assertThat(saved.getContextPolicyJson()).contains("\"session\":\"summary\"");
        assertThat(saved.getMemoryPolicyJson()).contains("\"longTerm\":\"disabled\"");
        assertThat(saved.getOrchestrationPolicyJson()).contains("\"executionMode\":\"multi_agent\"");
        assertThat(saved.getStatus()).isEqualTo("draft");
        assertThat(saved.getOwnerUserId()).isEqualTo(300L);
        assertThat(saved.getConfigJson()).contains("\"modelProviderId\":10", "\"modelConfigId\":11");
        assertThat(response.getAgentId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("draft");
    }

    @Test
    void getAgentShouldReturnTenantScopedAgent() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setTenantId(100L);
        agent.setAgentName("project assistant");
        when(agentMapper.selectOne(any(Wrapper.class))).thenReturn(agent);

        Agent result = agentService.getAgent(1L);

        assertThat(result.getAgentName()).isEqualTo("project assistant");
    }

    @Test
    void getAgentShouldRejectMissingOrCrossTenantAgent() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(agentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> agentService.getAgent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent not found");
    }

    @Test
    void createDraftShouldRejectMissingTenantContext() {
        UserContextHolder.clear();
        CreateAgentDraftCommand command = new CreateAgentDraftCommand();
        command.setAgentName("project assistant");

        assertThatThrownBy(() -> agentService.createDraft(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing tenant context");

        verify(agentMapper, never()).insert(any(Agent.class));
    }

    @Test
    void createDraftShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        CreateAgentDraftCommand command = new CreateAgentDraftCommand();
        command.setAgentName("project assistant");

        assertThatThrownBy(() -> agentService.createDraft(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(agentMapper, never()).insert(any(Agent.class));
    }

    @Test
    void createDraftShouldFillAgentDefinitionDefaults() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            agent.setId(2L);
            return 1;
        });
        CreateAgentDraftCommand command = new CreateAgentDraftCommand();
        command.setAgentName("report agent");
        command.setOwnerUserId(300L);

        agentService.createDraft(command);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentMapper).insert(captor.capture());
        Agent saved = captor.getValue();
        assertThat(saved.getAgentType()).isEqualTo("project_assistant");
        assertThat(saved.getCapabilityJson()).isEqualTo("[]");
        assertThat(saved.getToolPolicyJson()).isEqualTo("{}");
        assertThat(saved.getContextPolicyJson()).isEqualTo("{}");
        assertThat(saved.getMemoryPolicyJson()).isEqualTo("{}");
        assertThat(saved.getOrchestrationPolicyJson()).contains("\"executionMode\":\"single_agent\"");
    }
}
