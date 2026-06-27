package com.xiaoai.agent.plugin.integration;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.plugin.entity.PluginManifest;
import com.xiaoai.agent.plugin.mapper.PluginManifestMapper;
import com.xiaoai.agent.plugin.model.ImportPluginManifestCommand;
import com.xiaoai.agent.plugin.model.PluginManifestResponse;
import com.xiaoai.agent.plugin.service.impl.PluginManifestServiceImpl;
import com.xiaoai.agent.runtime.gateway.JavaInProcessRuntimeGateway;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginRuntimeFlowTest {

    private final PluginManifestMapper pluginManifestMapper = mock(PluginManifestMapper.class);
    private final ToolConfigService toolConfigService = mock(ToolConfigService.class);
    private final AgentService agentService = mock(AgentService.class);
    private final AgentToolBindingService agentToolBindingService = mock(AgentToolBindingService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final KnowledgeDocumentService knowledgeDocumentService = mock(KnowledgeDocumentService.class);
    private final RuntimeCheckpointService runtimeCheckpointService = mock(RuntimeCheckpointService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PluginManifestServiceImpl pluginManifestService = new PluginManifestServiceImpl(
            toolConfigService,
            agentService,
            agentToolBindingService,
            agentVersionService,
            objectMapper
    );
    private final JavaInProcessRuntimeGateway runtimeGateway = new JavaInProcessRuntimeGateway(
            toolConfigService,
            agentVersionService,
            modelGateway,
            knowledgeDocumentService,
            runtimeCheckpointService,
            new com.xiaoai.agent.runtime.engine.AgentRunEngine(objectMapper),
            objectMapper
    );

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(pluginManifestService, pluginManifestMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-plugin-runtime")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void importedPluginToolShouldBeCallableByRuntimeThroughDraftAgentVersionScope() {
        AgentVersion draftVersion = new AgentVersion();
        draftVersion.setId(13L);
        draftVersion.setTenantId(100L);
        draftVersion.setAgentId(300L);
        draftVersion.setVersionStatus("draft");
        draftVersion.setToolScopeJson("[]");
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
        when(agentService.getAgent(300L)).thenReturn(new Agent());
        when(agentVersionService.getVersion(13L)).thenReturn(draftVersion);
        when(agentVersionService.replaceVersionTools(org.mockito.ArgumentMatchers.eq(300L),
                org.mockito.ArgumentMatchers.eq(13L), any(UpdateAgentConfigCommand.class))).thenAnswer(invocation -> {
            UpdateAgentConfigCommand command = invocation.getArgument(2);
            draftVersion.setToolScopeJson("[{\"toolId\":22,\"toolCode\":\"controlled.cli.project-report\"}]");
            return AgentVersionResponse.builder()
                    .agentVersionId(13L)
                    .versionStatus("draft")
                    .toolIds(command.getToolIds())
                    .build();
        });
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .toolCode("controlled.cli.project-report")
                        .toolType("cli")
                        .riskLevel("medium")
                        .resultJson("{\"rows\":1}")
                        .build()
        );

        ImportPluginManifestCommand importCommand = new ImportPluginManifestCommand();
        importCommand.setAgentId(300L);
        importCommand.setAgentVersionId(13L);
        importCommand.setManifestJson(validManifestJson());
        PluginManifestResponse plugin = pluginManifestService.importManifest(importCommand);

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(300L)
                .agentVersionId(plugin.getBoundAgentVersionId())
                .taskId(1L)
                .runId(99L)
                .inputText("{\"toolId\":22,\"toolCode\":\"controlled.cli.project-report\",\"callPayloadJson\":{\"query\":\"risk\"}}")
                .traceId("trace-plugin-runtime")
                .build());

        assertThat(plugin.getBoundAgentVersionId()).isEqualTo(13L);
        assertThat(plugin.getAgentVersionToolIds()).containsExactly(22L);
        ArgumentCaptor<ExecuteToolCallCommand> toolCaptor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService).executeToolCall(toolCaptor.capture());
        assertThat(toolCaptor.getValue().getToolId()).isEqualTo(22L);
        assertThat(toolCaptor.getValue().getAgentVersionId()).isEqualTo(13L);
        assertThat(toolCaptor.getValue().getCallPayloadJson()).isEqualTo("{\"query\":\"risk\"}");
        assertThat(runtimeGateway.listEvents(99L)).extracting("eventType")
                .containsExactly("TOOL_CALL", "TOOL_RESULT");
    }

    @Test
    void importedPluginToolsShouldRunAsScopedRuntimeToolChain() {
        AgentVersion draftVersion = new AgentVersion();
        draftVersion.setId(14L);
        draftVersion.setTenantId(100L);
        draftVersion.setAgentId(300L);
        draftVersion.setVersionStatus("draft");
        draftVersion.setToolScopeJson("[]");
        when(pluginManifestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(pluginManifestMapper.insert(any(PluginManifest.class))).thenAnswer(invocation -> {
            PluginManifest plugin = invocation.getArgument(0);
            plugin.setId(12L);
            return 1;
        });
        when(toolConfigService.getOne(any())).thenReturn(null);
        when(toolConfigService.saveOrUpdate(any(ToolConfig.class))).thenAnswer(invocation -> {
            ToolConfig tool = invocation.getArgument(0);
            if ("controlled.http.project-query".equals(tool.getToolCode())) {
                tool.setId(31L);
            } else if ("controlled.cli.project-update".equals(tool.getToolCode())) {
                tool.setId(32L);
            }
            return true;
        });
        when(agentService.getAgent(300L)).thenReturn(new Agent());
        when(agentVersionService.getVersion(14L)).thenReturn(draftVersion);
        when(agentVersionService.replaceVersionTools(org.mockito.ArgumentMatchers.eq(300L),
                org.mockito.ArgumentMatchers.eq(14L), any(UpdateAgentConfigCommand.class))).thenAnswer(invocation -> {
            UpdateAgentConfigCommand command = invocation.getArgument(2);
            draftVersion.setToolScopeJson("[{\"toolId\":31,\"toolCode\":\"controlled.http.project-query\"},"
                    + "{\"toolId\":32,\"toolCode\":\"controlled.cli.project-update\"}]");
            return AgentVersionResponse.builder()
                    .agentVersionId(14L)
                    .versionStatus("draft")
                    .toolIds(command.getToolIds())
                    .build();
        });
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class)))
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(71L)
                        .toolCode("controlled.http.project-query")
                        .toolType("http")
                        .riskLevel("low")
                        .resultJson("{\"rows\":1}")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(72L)
                        .toolCode("controlled.cli.project-update")
                        .toolType("cli")
                        .riskLevel("medium")
                        .resultJson("{\"updated\":1}")
                        .build());

        ImportPluginManifestCommand importCommand = new ImportPluginManifestCommand();
        importCommand.setAgentId(300L);
        importCommand.setAgentVersionId(14L);
        importCommand.setManifestJson(multiToolManifestJson());
        PluginManifestResponse plugin = pluginManifestService.importManifest(importCommand);

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(300L)
                .agentVersionId(plugin.getBoundAgentVersionId())
                .taskId(2L)
                .runId(100L)
                .inputText("{\"toolCalls\":["
                        + "{\"toolId\":31,\"toolCode\":\"controlled.http.project-query\",\"toolType\":\"http\",\"callPayloadJson\":{\"query\":\"risk\"}},"
                        + "{\"toolId\":32,\"toolCode\":\"controlled.cli.project-update\",\"toolType\":\"cli\",\"callPayloadJson\":{\"id\":1,\"status\":\"done\"}}"
                        + "]}")
                .traceId("trace-plugin-runtime")
                .build());

        assertThat(plugin.getBoundAgentVersionId()).isEqualTo(14L);
        assertThat(plugin.getAgentVersionToolIds()).containsExactly(31L, 32L);
        assertThat(plugin.getTools()).extracting("toolCode")
                .containsExactly("controlled.http.project-query", "controlled.cli.project-update");
        ArgumentCaptor<ExecuteToolCallCommand> toolCaptor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService, org.mockito.Mockito.times(2)).executeToolCall(toolCaptor.capture());
        assertThat(toolCaptor.getAllValues()).extracting(ExecuteToolCallCommand::getToolId)
                .containsExactly(31L, 32L);
        assertThat(toolCaptor.getAllValues()).extracting(ExecuteToolCallCommand::getAgentVersionId)
                .containsExactly(14L, 14L);
        assertThat(toolCaptor.getAllValues()).extracting(ExecuteToolCallCommand::getCallPayloadJson)
                .containsExactly("{\"query\":\"risk\"}", "{\"id\":1,\"status\":\"done\"}");
        assertThat(runtimeGateway.listEvents(100L)).extracting("eventType")
                .containsExactly("TOOL_CALL", "TOOL_RESULT", "TOOL_CALL", "TOOL_RESULT");
        assertThat(runtimeGateway.listEvents(100L).get(0).getPayloadJson()).contains("\"toolCallIndex\":0");
        assertThat(runtimeGateway.listEvents(100L).get(2).getPayloadJson()).contains("\"toolCallIndex\":1");
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
                + "\"schema\":{\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}},\"additionalProperties\":false},"
                + "\"authConfig\":{\"workingDirectory\":\"" + escapedWorkingDirectory() + "\"}"
                + "}]"
                + "}";
    }

    private String multiToolManifestJson() {
        return "{"
                + "\"pluginCode\":\"project-business-suite\","
                + "\"pluginName\":\"Project Business Suite\","
                + "\"pluginVersion\":\"1.0.0\","
                + "\"tools\":[{"
                + "\"toolCode\":\"controlled.http.project-query\","
                + "\"toolName\":\"Project Query\","
                + "\"toolType\":\"http\","
                + "\"riskLevel\":\"low\","
                + "\"endpointUrl\":\"https://api.example.com/project/query\","
                + "\"schema\":{\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}},\"additionalProperties\":false},"
                + "\"authConfig\":{\"headers\":{\"X-Tenant\":\"demo\"}}"
                + "},{"
                + "\"toolCode\":\"controlled.cli.project-update\","
                + "\"toolName\":\"Project Update\","
                + "\"toolType\":\"cli\","
                + "\"riskLevel\":\"medium\","
                + "\"endpointUrl\":\"E:\\\\tools\\\\project-update.exe\","
                + "\"schema\":{\"required\":[\"id\",\"status\"],\"properties\":{\"id\":{\"type\":\"integer\"},\"status\":{\"type\":\"string\"}},\"additionalProperties\":false},"
                + "\"authConfig\":{\"workingDirectory\":\"" + escapedWorkingDirectory() + "\"}"
                + "}]"
                + "}";
    }

    private String escapedWorkingDirectory() {
        return Path.of("").toAbsolutePath().toString().replace("\\", "\\\\");
    }
}
