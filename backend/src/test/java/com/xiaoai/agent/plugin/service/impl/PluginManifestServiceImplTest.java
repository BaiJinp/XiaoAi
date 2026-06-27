package com.xiaoai.agent.plugin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.plugin.entity.PluginManifest;
import com.xiaoai.agent.plugin.mapper.PluginManifestMapper;
import com.xiaoai.agent.plugin.model.ImportPluginManifestCommand;
import com.xiaoai.agent.plugin.model.PluginManifestResponse;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginManifestServiceImplTest {

    private final PluginManifestMapper pluginManifestMapper = mock(PluginManifestMapper.class);
    private final ToolConfigService toolConfigService = mock(ToolConfigService.class);
    private final AgentService agentService = mock(AgentService.class);
    private final AgentToolBindingService agentToolBindingService = mock(AgentToolBindingService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final PluginManifestServiceImpl pluginManifestService =
            new PluginManifestServiceImpl(toolConfigService, agentService, agentToolBindingService, agentVersionService, new com.fasterxml.jackson.databind.ObjectMapper());

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(pluginManifestService, pluginManifestMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void importManifestShouldPersistPluginAndRegisterCliTool() {
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(pluginManifestMapper.insert(any(PluginManifest.class))).thenAnswer(invocation -> {
            PluginManifest plugin = invocation.getArgument(0);
            plugin.setId(11L);
            return 1;
        });
        when(toolConfigService.getOne(any())).thenReturn(null);
        when(toolConfigService.saveOrUpdate(any(ToolConfig.class))).thenAnswer(invocation -> {
            ToolConfig tool = invocation.getArgument(0);
            tool.setId(22L);
            return true;
        });

        PluginManifestResponse response = pluginManifestService.importManifest(command(validManifestJson()));

        ArgumentCaptor<PluginManifest> pluginCaptor = ArgumentCaptor.forClass(PluginManifest.class);
        verify(pluginManifestMapper).insert(pluginCaptor.capture());
        assertThat(pluginCaptor.getValue().getTenantId()).isEqualTo(100L);
        assertThat(pluginCaptor.getValue().getPluginCode()).isEqualTo("project-cli");
        assertThat(pluginCaptor.getValue().getPluginVersion()).isEqualTo("1.0.0");
        assertThat(pluginCaptor.getValue().getManifestJson()).contains("\"_audit\"");
        assertThat(pluginCaptor.getValue().getManifestJson()).contains("\"sourceType\":\"plugin_manifest\"");
        assertThat(pluginCaptor.getValue().getManifestJson()).contains("\"manifestHash\":\"");

        ArgumentCaptor<ToolConfig> toolCaptor = ArgumentCaptor.forClass(ToolConfig.class);
        verify(toolConfigService).saveOrUpdate(toolCaptor.capture());
        ToolConfig tool = toolCaptor.getValue();
        assertThat(tool.getTenantId()).isEqualTo(100L);
        assertThat(tool.getToolCode()).isEqualTo("controlled.cli.project-report");
        assertThat(tool.getToolType()).isEqualTo("cli");
        assertThat(tool.getRiskLevel()).isEqualTo("medium");
        assertThat(tool.getSchemaJson()).contains("\"required\"");
        assertThat(tool.getAuthConfigJson()).contains("workingDirectory");
        assertThat(tool.getAuthConfigJson()).contains("\"_plugin\"");
        assertThat(tool.getAuthConfigJson()).contains("\"_policy\"");
        assertThat(tool.getAuthConfigJson()).contains("\"pluginCode\":\"project-cli\"");
        assertThat(tool.getAuthConfigJson()).contains("\"pluginVersion\":\"1.0.0\"");
        assertThat(tool.getAuthConfigJson()).contains("\"manifestHash\":\"");
        assertThat(tool.getAuthConfigJson()).contains("\"allowedExecutable\":\"E:\\\\tools\\\\report.exe\"");
        assertThat(tool.getAuthConfigJson()).contains("\"allowedWorkingDirectory\"");
        assertThat(response.getPluginId()).isEqualTo(11L);
        assertThat(response.getManifestHash()).isNotBlank();
        assertThat(response.getTools()).hasSize(1);
        assertThat(response.getTools().get(0).getToolCode()).isEqualTo("controlled.cli.project-report");
        assertThat(response.getTools().get(0).getSchemaJson()).contains("\"required\"");
        assertThat(response.getTools().get(0).getBound()).isFalse();
        assertThat(response.getTools().get(0).getPluginCode()).isEqualTo("project-cli");
        assertThat(response.getTools().get(0).getPluginVersion()).isEqualTo("1.0.0");
        assertThat(response.getTools().get(0).getManifestHash()).isEqualTo(response.getManifestHash());
        verify(agentToolBindingService, never()).saveOrUpdate(any(AgentToolBinding.class));
    }

    @Test
    void importManifestShouldBindToolsToAgentWhenAgentIdProvided() {
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(pluginManifestMapper.insert(any(PluginManifest.class))).thenAnswer(invocation -> {
            PluginManifest plugin = invocation.getArgument(0);
            plugin.setId(11L);
            return 1;
        });
        when(toolConfigService.getOne(any())).thenReturn(null);
        when(toolConfigService.saveOrUpdate(any(ToolConfig.class))).thenAnswer(invocation -> {
            ToolConfig tool = invocation.getArgument(0);
            tool.setId(22L);
            return true;
        });
        when(agentToolBindingService.getOne(any())).thenReturn(null);
        when(agentToolBindingService.saveOrUpdate(any(AgentToolBinding.class))).thenAnswer(invocation -> {
            AgentToolBinding binding = invocation.getArgument(0);
            binding.setId(33L);
            return true;
        });
        when(agentService.getAgent(300L)).thenReturn(new Agent());

        ImportPluginManifestCommand command = command(validManifestJson());
        command.setAgentId(300L);
        PluginManifestResponse response = pluginManifestService.importManifest(command);

        ArgumentCaptor<AgentToolBinding> bindingCaptor = ArgumentCaptor.forClass(AgentToolBinding.class);
        verify(agentToolBindingService).saveOrUpdate(bindingCaptor.capture());
        AgentToolBinding binding = bindingCaptor.getValue();
        assertThat(binding.getTenantId()).isEqualTo(100L);
        assertThat(binding.getAgentId()).isEqualTo(300L);
        assertThat(binding.getToolId()).isEqualTo(22L);
        assertThat(binding.getBindingStatus()).isEqualTo("active");
        assertThat(binding.getPolicyJson()).contains("\"source\":\"plugin_manifest\"");
        assertThat(binding.getPolicyJson()).contains("\"pluginCode\":\"project-cli\"");
        assertThat(response.getTools()).hasSize(1);
        assertThat(response.getTools().get(0).getBound()).isTrue();
        assertThat(response.getTools().get(0).getBoundAgentId()).isEqualTo(300L);
        assertThat(response.getTools().get(0).getBindingId()).isEqualTo(33L);
    }

    @Test
    void importManifestShouldAppendToolsToDraftAgentVersionWhenVersionIdProvided() {
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(pluginManifestMapper.insert(any(PluginManifest.class))).thenAnswer(invocation -> {
            PluginManifest plugin = invocation.getArgument(0);
            plugin.setId(11L);
            return 1;
        });
        when(toolConfigService.getOne(any())).thenReturn(null);
        when(toolConfigService.saveOrUpdate(any(ToolConfig.class))).thenAnswer(invocation -> {
            ToolConfig tool = invocation.getArgument(0);
            tool.setId(22L);
            return true;
        });
        AgentVersion version = new AgentVersion();
        version.setId(13L);
        version.setAgentId(300L);
        version.setVersionStatus("draft");
        version.setToolScopeJson("[{\"toolId\":21,\"toolCode\":\"controlled.cli.old\"}]");
        when(agentVersionService.getVersion(13L)).thenReturn(version);

        ImportPluginManifestCommand command = command(validManifestJson());
        command.setAgentId(300L);
        command.setAgentVersionId(13L);
        when(agentVersionService.replaceVersionTools(org.mockito.ArgumentMatchers.eq(300L), org.mockito.ArgumentMatchers.eq(13L), any(UpdateAgentConfigCommand.class)))
                .thenReturn(AgentVersionResponse.builder()
                        .agentVersionId(13L)
                        .versionStatus("draft")
                        .toolIds(List.of(21L, 22L))
                        .build());

        PluginManifestResponse response = pluginManifestService.importManifest(command);

        ArgumentCaptor<UpdateAgentConfigCommand> updateCaptor = ArgumentCaptor.forClass(UpdateAgentConfigCommand.class);
        verify(agentVersionService).replaceVersionTools(org.mockito.ArgumentMatchers.eq(300L), org.mockito.ArgumentMatchers.eq(13L), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getToolIds()).containsExactly(21L, 22L);
        assertThat(response.getBoundAgentVersionId()).isEqualTo(13L);
        assertThat(response.getAgentVersionToolIds()).containsExactly(21L, 22L);
    }

    @Test
    void importManifestShouldCreateNewVersionAndDeactivateOtherVersions() {
        PluginManifest oldVersion = new PluginManifest();
        oldVersion.setId(10L);
        oldVersion.setTenantId(100L);
        oldVersion.setPluginCode("project-cli");
        oldVersion.setPluginVersion("1.0.0");
        oldVersion.setStatus("active");
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(pluginManifestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(oldVersion));
        when(pluginManifestMapper.insert(any(PluginManifest.class))).thenAnswer(invocation -> {
            PluginManifest plugin = invocation.getArgument(0);
            plugin.setId(11L);
            return 1;
        });
        when(toolConfigService.getOne(any())).thenReturn(null);
        when(toolConfigService.saveOrUpdate(any(ToolConfig.class))).thenAnswer(invocation -> {
            ToolConfig tool = invocation.getArgument(0);
            tool.setId(22L);
            return true;
        });

        PluginManifestResponse response = pluginManifestService.importManifest(command(validManifestJson().replace("\"pluginVersion\":\"1.0.0\"", "\"pluginVersion\":\"1.1.0\"")));

        verify(pluginManifestMapper).insert(any(PluginManifest.class));
        verify(pluginManifestMapper).updateById(oldVersion);
        assertThat(oldVersion.getStatus()).isEqualTo("inactive");
        assertThat(response.getPluginVersion()).isEqualTo("1.1.0");
    }

    @Test
    void importManifestShouldRegisterHttpTool() {
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(pluginManifestMapper.insert(any(PluginManifest.class))).thenAnswer(invocation -> {
            PluginManifest plugin = invocation.getArgument(0);
            plugin.setId(11L);
            return 1;
        });
        when(toolConfigService.getOne(any())).thenReturn(null);
        when(toolConfigService.saveOrUpdate(any(ToolConfig.class))).thenAnswer(invocation -> {
            ToolConfig tool = invocation.getArgument(0);
            tool.setId(22L);
            return true;
        });

        PluginManifestResponse response = pluginManifestService.importManifest(command(httpManifestJson()));

        ArgumentCaptor<ToolConfig> toolCaptor = ArgumentCaptor.forClass(ToolConfig.class);
        verify(toolConfigService).saveOrUpdate(toolCaptor.capture());
        ToolConfig tool = toolCaptor.getValue();
        assertThat(tool.getToolCode()).isEqualTo("controlled.http.project-query");
        assertThat(tool.getToolType()).isEqualTo("http");
        assertThat(tool.getEndpointUrl()).isEqualTo("https://api.example.com/project/query");
        assertThat(tool.getSchemaJson()).contains("\"required\"");
        assertThat(tool.getAuthConfigJson()).contains("\"headers\"");
        assertThat(tool.getAuthConfigJson()).contains("\"_plugin\"");
        assertThat(tool.getAuthConfigJson()).contains("\"_policy\"");
        assertThat(tool.getAuthConfigJson()).contains("\"pluginCode\":\"project-http\"");
        assertThat(tool.getAuthConfigJson()).contains("\"allowedHost\":\"api.example.com\"");
        assertThat(tool.getAuthConfigJson()).contains("\"allowedScheme\":\"https\"");
        assertThat(response.getTools()).hasSize(1);
        assertThat(response.getTools().get(0).getToolCode()).isEqualTo("controlled.http.project-query");
        assertThat(response.getTools().get(0).getSchemaJson()).contains("\"query\"");
        assertThat(response.getTools().get(0).getPluginCode()).isEqualTo("project-http");
    }

    @Test
    void importManifestShouldRejectInvalidJson() {
        assertThatThrownBy(() -> pluginManifestService.importManifest(command("{bad json")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plugin manifest is invalid JSON");
        verify(toolConfigService, never()).saveOrUpdate(any(ToolConfig.class));
    }

    @Test
    void importManifestShouldRejectCliToolCodeOutsideControlledNamespace() {
        String manifestJson = validManifestJson().replace("controlled.cli.project-report", "cli.project-report");

        assertThatThrownBy(() -> pluginManifestService.importManifest(command(manifestJson)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI toolCode must start with controlled.cli.");
        verify(toolConfigService, never()).saveOrUpdate(any(ToolConfig.class));
    }

    @Test
    void importManifestShouldRejectHttpToolCodeOutsideControlledNamespace() {
        String manifestJson = httpManifestJson().replace("controlled.http.project-query", "http.project-query");

        assertThatThrownBy(() -> pluginManifestService.importManifest(command(manifestJson)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP toolCode must start with controlled.http.");
        verify(toolConfigService, never()).saveOrUpdate(any(ToolConfig.class));
    }

    @Test
    void importManifestShouldRejectMissingWorkingDirectory() {
        String manifestJson = validManifestJson().replace("\"authConfig\":{\"workingDirectory\":\""
                + escapedWorkingDirectory() + "\"}", "\"authConfig\":{}");

        assertThatThrownBy(() -> pluginManifestService.importManifest(command(manifestJson)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI workingDirectory is required");
        verify(toolConfigService, never()).saveOrUpdate(any(ToolConfig.class));
    }

    @Test
    void importManifestShouldRejectDuplicateToolCodesBeforePersisting() {
        String toolJson = "{"
                + "\"toolCode\":\"controlled.cli.project-report\","
                + "\"toolName\":\"Project Report Copy\","
                + "\"toolType\":\"cli\","
                + "\"riskLevel\":\"medium\","
                + "\"endpointUrl\":\"E:\\\\tools\\\\report-copy.exe\","
                + "\"schema\":{\"required\":[\"query\"],\"properties\":{\"query\":{}},\"additionalProperties\":false},"
                + "\"authConfig\":{\"workingDirectory\":\"" + escapedWorkingDirectory() + "\"}"
                + "}";
        String manifestJson = validManifestJson().replace("}]", "}," + toolJson + "]");

        assertThatThrownBy(() -> pluginManifestService.importManifest(command(manifestJson)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plugin manifest duplicate toolCode: controlled.cli.project-report");
        verify(pluginManifestMapper, never()).insert(any(PluginManifest.class));
        verify(toolConfigService, never()).saveOrUpdate(any(ToolConfig.class));
    }

    @Test
    void disablePluginShouldDisableManifestTools() {
        PluginManifest plugin = new PluginManifest();
        plugin.setId(11L);
        plugin.setTenantId(100L);
        plugin.setPluginCode("project-cli");
        plugin.setPluginName("Project CLI");
        plugin.setPluginVersion("1.0.0");
        plugin.setManifestJson(validManifestJson());
        plugin.setStatus("active");
        ToolConfig tool = new ToolConfig();
        tool.setId(22L);
        tool.setTenantId(100L);
        tool.setToolCode("controlled.cli.project-report");
        tool.setStatus("active");
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(plugin);
        when(toolConfigService.getOne(any())).thenReturn(tool);

        PluginManifestResponse response = pluginManifestService.disablePlugin(11L);

        verify(pluginManifestMapper).updateById(plugin);
        verify(toolConfigService).saveOrUpdate(tool);
        assertThat(plugin.getStatus()).isEqualTo("inactive");
        assertThat(tool.getStatus()).isEqualTo("inactive");
        assertThat(response.getStatus()).isEqualTo("inactive");
    }

    @Test
    void disablePluginShouldKeepSharedToolActiveWhenAnotherActiveVersionDeclaresIt() {
        PluginManifest oldVersion = new PluginManifest();
        oldVersion.setId(11L);
        oldVersion.setTenantId(100L);
        oldVersion.setPluginCode("project-cli");
        oldVersion.setPluginName("Project CLI");
        oldVersion.setPluginVersion("1.0.0");
        oldVersion.setManifestJson(validManifestJson());
        oldVersion.setStatus("active");
        PluginManifest activeVersion = new PluginManifest();
        activeVersion.setId(12L);
        activeVersion.setTenantId(100L);
        activeVersion.setPluginCode("project-cli");
        activeVersion.setPluginName("Project CLI");
        activeVersion.setPluginVersion("1.1.0");
        activeVersion.setManifestJson(validManifestJson().replace("\"pluginVersion\":\"1.0.0\"", "\"pluginVersion\":\"1.1.0\""));
        activeVersion.setStatus("active");
        ToolConfig tool = new ToolConfig();
        tool.setId(22L);
        tool.setTenantId(100L);
        tool.setToolCode("controlled.cli.project-report");
        tool.setStatus("active");
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(oldVersion);
        when(pluginManifestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeVersion));
        when(toolConfigService.getOne(any())).thenReturn(tool);

        PluginManifestResponse response = pluginManifestService.disablePlugin(11L);

        verify(pluginManifestMapper).updateById(oldVersion);
        verify(toolConfigService).saveOrUpdate(tool);
        assertThat(oldVersion.getStatus()).isEqualTo("inactive");
        assertThat(tool.getStatus()).isEqualTo("active");
        assertThat(response.getStatus()).isEqualTo("inactive");
    }

    @Test
    void enablePluginShouldRejectMissingManifest() {
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> pluginManifestService.enablePlugin(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plugin manifest not found");
    }

    private ImportPluginManifestCommand command(String manifestJson) {
        ImportPluginManifestCommand command = new ImportPluginManifestCommand();
        command.setManifestJson(manifestJson);
        return command;
    }

    private String validManifestJson() {
        return "{"
                + "\"pluginCode\":\"project-cli\","
                + "\"pluginName\":\"Project CLI\","
                + "\"pluginVersion\":\"1.0.0\","
                + "\"tools\":[{"
                + "\"toolCode\":\"controlled.cli.project-report\","
                + "\"toolName\":\"Project Report\","
                + "\"toolType\":\"cli\","
                + "\"riskLevel\":\"medium\","
                + "\"endpointUrl\":\"E:\\\\tools\\\\report.exe\","
                + "\"schema\":{\"required\":[\"query\"],\"properties\":{\"query\":{}},\"additionalProperties\":false},"
                + "\"authConfig\":{\"workingDirectory\":\"" + escapedWorkingDirectory() + "\"}"
                + "}]"
                + "}";
    }

    private String httpManifestJson() {
        return "{"
                + "\"pluginCode\":\"project-http\","
                + "\"pluginName\":\"Project HTTP\","
                + "\"pluginVersion\":\"1.0.0\","
                + "\"tools\":[{"
                + "\"toolCode\":\"controlled.http.project-query\","
                + "\"toolName\":\"Project Query\","
                + "\"toolType\":\"http\","
                + "\"riskLevel\":\"medium\","
                + "\"endpointUrl\":\"https://api.example.com/project/query\","
                + "\"schema\":{\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}},\"additionalProperties\":false},"
                + "\"authConfig\":{\"headers\":{\"X-Tenant\":\"demo\"}}"
                + "}]"
                + "}";
    }

    private String escapedWorkingDirectory() {
        return Path.of("").toAbsolutePath().toString().replace("\\", "\\\\");
    }
}
