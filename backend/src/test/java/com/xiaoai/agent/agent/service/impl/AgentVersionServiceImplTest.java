package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVersionServiceImplTest {

    private final AgentVersionMapper agentVersionMapper = mock(AgentVersionMapper.class);
    private final AgentVersionServiceImpl agentVersionService = new AgentVersionServiceImpl();

    AgentVersionServiceImplTest() {
        TestReflectionUtils.injectBaseMapper(agentVersionService, agentVersionMapper);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createVersionShouldUseDefaultJsonWhenCommandFieldsBlank() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(agentVersionMapper.insert(any(AgentVersion.class))).thenAnswer(invocation -> {
            AgentVersion version = invocation.getArgument(0);
            version.setId(10L);
            return 1;
        });
        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();
        command.setRolePrompt("你是项目助理");

        AgentVersionResponse response = agentVersionService.createVersion(1L, command);

        ArgumentCaptor<AgentVersion> captor = ArgumentCaptor.forClass(AgentVersion.class);
        verify(agentVersionMapper).insert(captor.capture());
        AgentVersion saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getAgentId()).isEqualTo(1L);
        assertThat(saved.getVersionStatus()).isEqualTo("draft");
        assertThat(saved.getConfigJson()).isEqualTo("{}");
        assertThat(saved.getKnowledgeScopeJson()).isEqualTo("[]");
        assertThat(saved.getToolScopeJson()).isEqualTo("[]");
        assertThat(response.getAgentVersionId()).isEqualTo(10L);
        assertThat(response.getToolIds()).isEmpty();
    }

    @Test
    void createVersionShouldSnapshotRuntimePolicies() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(agentVersionMapper.insert(any(AgentVersion.class))).thenAnswer(invocation -> {
            AgentVersion version = invocation.getArgument(0);
            version.setId(10L);
            return 1;
        });
        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();
        command.setModelPolicyJson("{\"provider\":\"agent_default\"}");
        command.setContextPolicyJson("{\"maxItems\":8}");
        command.setMemoryPolicyJson("{\"write\":\"confirmed_only\",\"enabled\":true,\"maxItems\":5,\"scopes\":[\"task\"]}");
        command.setOrchestrationPolicyJson("{\"executionMode\":\"dynamic_workflow\"}");

        AgentVersionResponse response = agentVersionService.createVersion(1L, command);

        ArgumentCaptor<AgentVersion> captor = ArgumentCaptor.forClass(AgentVersion.class);
        verify(agentVersionMapper).insert(captor.capture());
        AgentVersion saved = captor.getValue();
        assertThat(saved.getRuntimeSnapshotJson()).contains("\"modelPolicy\":{\"provider\":\"agent_default\"}");
        assertThat(saved.getRuntimeSnapshotJson()).contains("\"contextPolicy\":{\"maxItems\":8}");
        assertThat(saved.getRuntimeSnapshotJson()).contains("\"memoryPolicy\":{\"write\":\"confirmed_only\",\"enabled\":true,\"maxItems\":5,\"scopes\":[\"task\"]}");
        assertThat(saved.getRuntimeSnapshotJson()).contains("\"orchestrationPolicy\":{\"executionMode\":\"dynamic_workflow\"}");
        assertThat(response.getRuntimeSnapshotJson()).isEqualTo(saved.getRuntimeSnapshotJson());
        assertThat(response.getOrchestrationPolicyJson()).isEqualTo("{\"executionMode\":\"dynamic_workflow\"}");
    }

    @Test
    void createVersionShouldRejectInvalidPolicyJson() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();
        command.setMemoryPolicyJson("not-json");

        assertThatThrownBy(() -> agentVersionService.createVersion(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent policy JSON must be valid JSON");

        verify(agentVersionMapper, never()).insert(any(AgentVersion.class));
    }

    @Test
    void createVersionShouldSnapshotAndBindSelectedTools() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        ToolConfigService toolConfigService = mock(ToolConfigService.class);
        AgentToolBindingService agentToolBindingService = mock(AgentToolBindingService.class);
        AgentVersionServiceImpl service = new AgentVersionServiceImpl(toolConfigService, agentToolBindingService, new ObjectMapper());
        TestReflectionUtils.injectBaseMapper(service, agentVersionMapper);
        when(agentVersionMapper.insert(any(AgentVersion.class))).thenAnswer(invocation -> {
            AgentVersion version = invocation.getArgument(0);
            version.setId(10L);
            return 1;
        });
        when(toolConfigService.getToolConfig(22L)).thenReturn(tool(22L, "controlled.cli.project-report", "medium"));
        when(toolConfigService.getToolConfig(23L)).thenReturn(tool(23L, "controlled.cli.project-query", "low"));

        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();
        command.setToolIds(List.of(22L, 23L, 22L));
        AgentVersionResponse response = service.createVersion(1L, command);

        ArgumentCaptor<AgentVersion> versionCaptor = ArgumentCaptor.forClass(AgentVersion.class);
        verify(agentVersionMapper).insert(versionCaptor.capture());
        AgentVersion saved = versionCaptor.getValue();
        assertThat(saved.getToolScopeJson()).contains("\"toolId\":22");
        assertThat(saved.getToolScopeJson()).contains("\"toolCode\":\"controlled.cli.project-report\"");
        assertThat(saved.getToolScopeJson()).contains("\"toolId\":23");

        ArgumentCaptor<AgentToolBinding> bindingCaptor = ArgumentCaptor.forClass(AgentToolBinding.class);
        verify(agentToolBindingService, org.mockito.Mockito.times(2)).saveOrUpdate(bindingCaptor.capture());
        assertThat(bindingCaptor.getAllValues())
                .extracting(AgentToolBinding::getToolId)
                .containsExactly(22L, 23L);
        assertThat(bindingCaptor.getAllValues())
                .allSatisfy(binding -> {
                    assertThat(binding.getTenantId()).isEqualTo(100L);
                    assertThat(binding.getAgentId()).isEqualTo(1L);
                    assertThat(binding.getAgentVersionId()).isEqualTo(10L);
                    assertThat(binding.getBindingStatus()).isEqualTo("active");
                    assertThat(binding.getPolicyJson()).isEqualTo("{\"source\":\"agent_version\"}");
                });
        assertThat(response.getToolIds()).containsExactly(22L, 23L);
    }

    @Test
    void getVersionShouldReturnTenantScopedVersion() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        AgentVersion version = new AgentVersion();
        version.setId(10L);
        version.setTenantId(100L);
        version.setVersionNo("v1");
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(version);

        AgentVersion result = agentVersionService.getVersion(10L);

        assertThat(result.getVersionNo()).isEqualTo("v1");
    }

    @Test
    void getVersionShouldRejectMissingOrCrossTenantVersion() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> agentVersionService.getVersion(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent version not found");
    }

    @Test
    void listVersionsByAgentShouldReturnTenantScopedVersionResponsesWithToolIds() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        AgentVersion draft = new AgentVersion();
        draft.setId(10L);
        draft.setTenantId(100L);
        draft.setAgentId(1L);
        draft.setVersionNo("v1");
        draft.setVersionStatus("draft");
        draft.setToolScopeJson("[{\"toolId\":22,\"toolCode\":\"controlled.cli.project-report\"}]");
        AgentVersion published = new AgentVersion();
        published.setId(11L);
        published.setTenantId(100L);
        published.setAgentId(1L);
        published.setVersionNo("v2");
        published.setVersionStatus("published");
        published.setToolScopeJson("[{\"toolId\":23,\"toolCode\":\"controlled.cli.project-query\"}]");
        when(agentVersionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(draft, published));

        List<AgentVersionResponse> responses = agentVersionService.listVersionsByAgent(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getAgentVersionId()).isEqualTo(10L);
        assertThat(responses.get(0).getVersionStatus()).isEqualTo("draft");
        assertThat(responses.get(0).getToolIds()).containsExactly(22L);
        assertThat(responses.get(1).getAgentVersionId()).isEqualTo(11L);
        assertThat(responses.get(1).getToolIds()).containsExactly(23L);
    }

    @Test
    void publishVersionShouldMarkDraftVersionPublished() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        AgentVersion version = new AgentVersion();
        version.setId(10L);
        version.setTenantId(100L);
        version.setAgentId(1L);
        version.setVersionStatus("draft");
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(version);

        AgentVersion result = agentVersionService.publishVersion(1L, 10L);

        assertThat(result.getVersionStatus()).isEqualTo("published");
        ArgumentCaptor<AgentVersion> captor = ArgumentCaptor.forClass(AgentVersion.class);
        verify(agentVersionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getToolScopeJson()).isNull();
    }

    @Test
    void publishVersionShouldRejectVersionFromAnotherAgent() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        AgentVersion version = new AgentVersion();
        version.setId(10L);
        version.setTenantId(100L);
        version.setAgentId(2L);
        version.setVersionStatus("draft");
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(version);

        assertThatThrownBy(() -> agentVersionService.publishVersion(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent version does not belong to agent");

        verify(agentVersionMapper, never()).updateById(any(AgentVersion.class));
    }

    @Test
    void publishVersionShouldKeepPublishedVersionIdempotent() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        AgentVersion version = new AgentVersion();
        version.setId(10L);
        version.setTenantId(100L);
        version.setAgentId(1L);
        version.setVersionStatus("published");
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(version);

        AgentVersion result = agentVersionService.publishVersion(1L, 10L);

        assertThat(result.getVersionStatus()).isEqualTo("published");
        verify(agentVersionMapper, never()).updateById(any(AgentVersion.class));
    }

    @Test
    void replaceVersionToolsShouldUpdateDraftScopeAndBindings() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        ToolConfigService toolConfigService = mock(ToolConfigService.class);
        AgentToolBindingService agentToolBindingService = mock(AgentToolBindingService.class);
        AgentVersionServiceImpl service = new AgentVersionServiceImpl(toolConfigService, agentToolBindingService, new ObjectMapper());
        TestReflectionUtils.injectBaseMapper(service, agentVersionMapper);
        AgentVersion version = new AgentVersion();
        version.setId(10L);
        version.setTenantId(100L);
        version.setAgentId(1L);
        version.setVersionNo("v1");
        version.setVersionStatus("draft");
        version.setToolScopeJson("[{\"toolId\":24,\"toolCode\":\"controlled.cli.old\"}]");
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(version);
        when(toolConfigService.getToolConfig(22L)).thenReturn(tool(22L, "controlled.cli.project-report", "medium"));
        when(toolConfigService.getToolConfig(23L)).thenReturn(tool(23L, "controlled.cli.project-query", "low"));
        AgentToolBinding keepBinding = binding(1L, 22L, "inactive");
        AgentToolBinding removeBinding = binding(2L, 24L, "active");
        when(agentToolBindingService.listVersionBindings(10L)).thenReturn(List.of(keepBinding, removeBinding));

        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();
        command.setToolIds(List.of(22L, 23L, 22L));
        AgentVersionResponse response = service.replaceVersionTools(1L, 10L, command);

        ArgumentCaptor<AgentVersion> versionCaptor = ArgumentCaptor.forClass(AgentVersion.class);
        verify(agentVersionMapper).updateById(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getToolScopeJson()).contains("\"toolId\":22");
        assertThat(versionCaptor.getValue().getToolScopeJson()).contains("\"toolId\":23");
        assertThat(versionCaptor.getValue().getToolScopeJson()).doesNotContain("\"toolId\":24");

        ArgumentCaptor<AgentToolBinding> bindingCaptor = ArgumentCaptor.forClass(AgentToolBinding.class);
        verify(agentToolBindingService, times(3)).saveOrUpdate(bindingCaptor.capture());
        assertThat(bindingCaptor.getAllValues())
                .extracting(AgentToolBinding::getToolId, AgentToolBinding::getBindingStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(22L, "active"),
                        org.assertj.core.groups.Tuple.tuple(23L, "active"),
                        org.assertj.core.groups.Tuple.tuple(24L, "inactive")
                );
        assertThat(response.getToolIds()).containsExactly(22L, 23L);
    }

    @Test
    void replaceVersionToolsShouldRejectPublishedVersion() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        ToolConfigService toolConfigService = mock(ToolConfigService.class);
        AgentToolBindingService agentToolBindingService = mock(AgentToolBindingService.class);
        AgentVersionServiceImpl service = new AgentVersionServiceImpl(toolConfigService, agentToolBindingService, new ObjectMapper());
        TestReflectionUtils.injectBaseMapper(service, agentVersionMapper);
        AgentVersion version = new AgentVersion();
        version.setId(10L);
        version.setTenantId(100L);
        version.setAgentId(1L);
        version.setVersionStatus("published");
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(version);
        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();
        command.setToolIds(List.of(22L));

        assertThatThrownBy(() -> service.replaceVersionTools(1L, 10L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only draft agent version can update tool scope");

        verify(agentVersionMapper, never()).updateById(any(AgentVersion.class));
        verify(agentToolBindingService, never()).saveOrUpdate(any(AgentToolBinding.class));
    }

    @Test
    void createVersionShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        UpdateAgentConfigCommand command = new UpdateAgentConfigCommand();

        assertThatThrownBy(() -> agentVersionService.createVersion(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(agentVersionMapper, never()).insert(any(AgentVersion.class));
    }

    private ToolConfig tool(Long id, String toolCode, String riskLevel) {
        ToolConfig tool = new ToolConfig();
        tool.setId(id);
        tool.setToolCode(toolCode);
        tool.setRiskLevel(riskLevel);
        return tool;
    }

    private AgentToolBinding binding(Long id, Long toolId, String status) {
        AgentToolBinding binding = new AgentToolBinding();
        binding.setId(id);
        binding.setTenantId(100L);
        binding.setAgentId(1L);
        binding.setAgentVersionId(10L);
        binding.setToolId(toolId);
        binding.setBindingStatus(status);
        return binding;
    }
}
