package com.xiaoai.agent.runtime.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.knowledge.model.KnowledgeRetrieveResult;
import com.xiaoai.agent.knowledge.model.RetrieveKnowledgeCommand;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunResumeCommand;
import com.xiaoai.agent.runtime.model.RunApprovalResultCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JavaInProcessRuntimeGatewayTest {

    private final ToolConfigService toolConfigService = mock(ToolConfigService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final KnowledgeDocumentService knowledgeDocumentService = mock(KnowledgeDocumentService.class);
    private final AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
    private final RuntimeCheckpointService runtimeCheckpointService = mock(RuntimeCheckpointService.class);
    private final JavaInProcessRuntimeGateway runtimeGateway = new JavaInProcessRuntimeGateway(
            toolConfigService,
            modelGateway,
            knowledgeDocumentService,
            runtimeCheckpointService,
            new ObjectMapper()
    );

    @Test
    void startRunShouldDenyToolOutsideAgentVersionScope() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolId\":22,\"toolCode\":\"controlled.cli.allowed\"}]");
        when(agentVersionService.getVersion(11L)).thenReturn(version);
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(120L)
                .inputText("{\"toolId\":10,\"toolCode\":\"controlled.cli.denied\",\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());

        verify(toolConfigService, org.mockito.Mockito.never()).executeToolCall(any(ExecuteToolCallCommand.class));
        List<RuntimeEvent> events = scopedGateway.listEvents(120L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_DENIED");
        assertThat(events.get(1).getPayloadJson()).contains(
                "\"agentVersionId\":11",
                "\"reason\":\"tool_not_in_agent_version_scope\""
        );
    }

    @Test
    void startRunShouldExecuteToolInsideAgentVersionScope() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolId\":10,\"toolCode\":\"controlled.cli.allowed\"}]");
        when(agentVersionService.getVersion(11L)).thenReturn(version);
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .resultJson("{\"ok\":true}")
                        .build()
        );
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(121L)
                .inputText("{\"toolId\":10,\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());

        verify(toolConfigService).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(scopedGateway.listEvents(121L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT");
    }

    @Test
    void startRunShouldExecuteToolMatchedByLegacyToolCodeScope() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolCode\":\"controlled.cli.allowed\"}]");
        when(agentVersionService.getVersion(11L)).thenReturn(version);
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .toolCode("controlled.cli.allowed")
                        .resultJson("{\"ok\":true}")
                        .build()
        );
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(122L)
                .inputText("{\"toolId\":10,\"toolCode\":\"controlled.cli.allowed\",\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());

        verify(toolConfigService).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(scopedGateway.listEvents(122L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT");
    }

    @Test
    void startRunShouldDenyToolOutsideLegacyToolCodeScope() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolCode\":\"controlled.cli.allowed\"}]");
        when(agentVersionService.getVersion(11L)).thenReturn(version);
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(123L)
                .inputText("{\"toolId\":10,\"toolCode\":\"controlled.cli.denied\",\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());

        verify(toolConfigService, org.mockito.Mockito.never()).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(scopedGateway.listEvents(123L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_DENIED");
    }

    @Test
    void startRunShouldExecuteRequestedToolAndCacheRuntimeEvents() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .resultJson("{\"ok\":true}")
                        .build()
        );

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(99L)
                .inputText("{\"toolId\":10,\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<ExecuteToolCallCommand> captor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService).executeToolCall(captor.capture());
        ExecuteToolCallCommand command = captor.getValue();
        assertThat(command.getToolId()).isEqualTo(10L);
        assertThat(command.getAgentVersionId()).isEqualTo(11L);
        assertThat(command.getCallPayloadJson()).isEqualTo("{\"amount\":100}");
        List<RuntimeEvent> events = runtimeGateway.listEvents(99L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT");
    }

    @Test
    void startRunShouldExecuteToolCallsSequentially() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class)))
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .toolType("http")
                        .toolCode("controlled.http.query")
                        .riskLevel("low")
                        .resultJson("{\"rows\":1}")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(67L)
                        .toolType("cli")
                        .toolCode("controlled.cli.update")
                        .riskLevel("medium")
                        .resultJson("{\"updated\":1}")
                        .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(127L)
                .inputText("{\"toolCalls\":["
                        + "{\"toolId\":10,\"toolCode\":\"controlled.http.query\",\"toolType\":\"http\",\"callPayloadJson\":{\"query\":\"risk\"}},"
                        + "{\"toolId\":11,\"toolCode\":\"controlled.cli.update\",\"toolType\":\"cli\",\"callPayloadJson\":{\"id\":1}}"
                        + "]}")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<ExecuteToolCallCommand> captor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService, org.mockito.Mockito.times(2)).executeToolCall(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExecuteToolCallCommand::getToolId)
                .containsExactly(10L, 11L);
        assertThat(captor.getAllValues()).extracting(ExecuteToolCallCommand::getCallPayloadJson)
                .containsExactly("{\"query\":\"risk\"}", "{\"id\":1}");
        List<RuntimeEvent> events = runtimeGateway.listEvents(127L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT", "TOOL_CALL", "TOOL_RESULT");
        assertThat(events.get(0).getPayloadJson()).contains("\"toolCallIndex\":0", "\"toolCode\":\"controlled.http.query\"");
        assertThat(events.get(2).getPayloadJson()).contains("\"toolCallIndex\":1", "\"toolCode\":\"controlled.cli.update\"");
    }

    @Test
    void startRunShouldStopToolCallsWhenOneIsBlocked() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class)))
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .toolCode("controlled.http.query")
                        .resultJson("{\"rows\":1}")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(67L)
                        .approvalRequestId(88L)
                        .toolType("cli")
                        .toolCode("controlled.cli.update")
                        .riskLevel("high")
                        .errorMessage("高风险工具审批")
                        .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(128L)
                .inputText("{\"toolCalls\":["
                        + "{\"toolId\":10,\"toolCode\":\"controlled.http.query\",\"callPayloadJson\":{\"query\":\"risk\"}},"
                        + "{\"toolId\":11,\"toolCode\":\"controlled.cli.update\",\"callPayloadJson\":{\"id\":1}},"
                        + "{\"toolId\":12,\"toolCode\":\"controlled.http.report\",\"callPayloadJson\":{\"format\":\"csv\"}}"
                        + "]}")
                .traceId("trace-1")
                .build());

        verify(toolConfigService, org.mockito.Mockito.times(2)).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(runtimeGateway.listEvents(128L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT", "TOOL_CALL", "TOOL_BLOCKED");
        RuntimeEvent blocked = runtimeGateway.listEvents(128L).get(3);
        assertThat(blocked.getPayloadJson()).contains("\"approvalRequestId\":88", "\"toolCallIndex\":1");
        ArgumentCaptor<RuntimeCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(RuntimeCheckpoint.class);
        verify(runtimeCheckpointService).save(checkpointCaptor.capture());
        assertThat(checkpointCaptor.getValue().getPayloadJson()).contains("\"toolCallIndex\":1");
    }

    @Test
    void resumeRunShouldContinueRemainingToolCallsAfterApproval() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class)))
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .toolCode("controlled.http.query")
                        .resultJson("{\"rows\":1}")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(67L)
                        .approvalRequestId(88L)
                        .toolType("cli")
                        .toolCode("controlled.cli.update")
                        .riskLevel("high")
                        .errorMessage("高风险工具审批")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(68L)
                        .toolType("cli")
                        .toolCode("controlled.cli.update")
                        .riskLevel("high")
                        .resultJson("{\"updated\":1}")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(69L)
                        .toolType("http")
                        .toolCode("controlled.http.report")
                        .riskLevel("low")
                        .resultJson("{\"reportId\":9}")
                        .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(129L)
                .inputText("{\"toolCalls\":["
                        + "{\"toolId\":10,\"toolCode\":\"controlled.http.query\",\"callPayloadJson\":{\"query\":\"risk\"}},"
                        + "{\"toolId\":11,\"toolCode\":\"controlled.cli.update\",\"callPayloadJson\":{\"id\":1}},"
                        + "{\"toolId\":12,\"toolCode\":\"controlled.http.report\",\"callPayloadJson\":{\"format\":\"csv\"}}"
                        + "]}")
                .traceId("trace-1")
                .build());
        runtimeGateway.submitApprovalResult(RunApprovalResultCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(129L)
                .approvalRequestId(88L)
                .approvalStatus("approved")
                .build());
        runtimeGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(129L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build());

        ArgumentCaptor<ExecuteToolCallCommand> captor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService, org.mockito.Mockito.times(4)).executeToolCall(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExecuteToolCallCommand::getToolId)
                .containsExactly(10L, 11L, 11L, 12L);
        assertThat(captor.getAllValues().get(2).getApprovalBypassed()).isTrue();
        assertThat(captor.getAllValues().get(2).getApprovalRequestId()).isEqualTo(88L);
        assertThat(captor.getAllValues().get(3).getApprovalBypassed()).isFalse();
        assertThat(runtimeGateway.listEvents(129L)).extracting(RuntimeEvent::getEventType)
                .containsExactly(
                        "TOOL_CALL",
                        "TOOL_RESULT",
                        "TOOL_CALL",
                        "TOOL_BLOCKED",
                        "TOOL_CALL",
                        "TOOL_RESULT",
                        "TOOL_CALL",
                        "TOOL_RESULT"
                );
        assertThat(runtimeGateway.listEvents(129L).get(5).getPayloadJson()).contains("\"toolCallIndex\":1");
        assertThat(runtimeGateway.listEvents(129L).get(6).getPayloadJson()).contains("\"toolCallIndex\":2");
    }

    @Test
    void startRunShouldStopToolCallsWhenOneIsOutsideAgentVersionScope() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolId\":10,\"toolCode\":\"controlled.http.query\"}]");
        when(agentVersionService.getVersion(11L)).thenReturn(version);
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(66L)
                        .toolCode("controlled.http.query")
                        .resultJson("{\"rows\":1}")
                        .build()
        );
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(130L)
                .inputText("{\"toolCalls\":["
                        + "{\"toolId\":10,\"toolCode\":\"controlled.http.query\",\"callPayloadJson\":{\"query\":\"risk\"}},"
                        + "{\"toolId\":11,\"toolCode\":\"controlled.cli.update\",\"callPayloadJson\":{\"id\":1}}"
                        + "]}")
                .traceId("trace-1")
                .build());

        verify(toolConfigService, org.mockito.Mockito.times(1)).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(scopedGateway.listEvents(130L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT", "TOOL_CALL", "TOOL_DENIED");
        assertThat(scopedGateway.listEvents(130L).get(3).getPayloadJson())
                .contains("\"toolCallIndex\":1", "\"reason\":\"tool_not_in_agent_version_scope\"");
    }

    @Test
    void startRunShouldCallModelGatewayAndCacheRuntimeEvents() {
        when(modelGateway.chat(any(ChatModelCommand.class))).thenReturn(ChatModelResponse.builder()
                .content("Mock response")
                .promptTokens(3)
                .completionTokens(4)
                .totalTokens(7)
                .modelCallLogId(77L)
                .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(100L)
                .inputText("{\"modelId\":1,\"prompt\":\"生成项目周报\"}")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<ChatModelCommand> captor = ArgumentCaptor.forClass(ChatModelCommand.class);
        verify(modelGateway).chat(captor.capture());
        ChatModelCommand command = captor.getValue();
        assertThat(command.getModelId()).isEqualTo(1L);
        assertThat(command.getPrompt()).isEqualTo("生成项目周报");
        List<RuntimeEvent> events = runtimeGateway.listEvents(100L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("MODEL_CALL", "MODEL_RESULT");
    }

    @Test
    void startRunShouldLoadConfirmedMemoryIntoModelPrompt() {
        AgentMemory memory = new AgentMemory();
        memory.setId(9L);
        memory.setMemoryType("session_summary");
        memory.setMemoryScope("task");
        memory.setConfidence("confirmed");
        memory.setSummaryText("周报必须按风险优先排序");
        when(agentMemoryService.listConfirmedMemoriesForRuntime(100L, 10L, 1L, null, 200L, List.of("task", "agent"), 3))
                .thenReturn(List.of(memory));
        when(modelGateway.chat(any(ChatModelCommand.class))).thenReturn(ChatModelResponse.builder()
                .content("Mock response")
                .promptTokens(3)
                .completionTokens(4)
                .totalTokens(7)
                .modelCallLogId(77L)
                .build());
        JavaInProcessRuntimeGateway memoryGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                agentMemoryService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        memoryGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(126L)
                .inputText("{\"modelId\":1,\"prompt\":\"生成项目周报\"}")
                .runtimeSnapshotJson("{\"memoryPolicy\":{\"enabled\":true,\"maxItems\":3,\"scopes\":[\"task\",\"agent\"]}}")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<ChatModelCommand> captor = ArgumentCaptor.forClass(ChatModelCommand.class);
        verify(modelGateway).chat(captor.capture());
        assertThat(captor.getValue().getPrompt()).contains("生成项目周报", "已确认记忆", "周报必须按风险优先排序");
        List<RuntimeEvent> events = memoryGateway.listEvents(126L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("MEMORY_CONTEXT", "MODEL_CALL", "MODEL_RESULT");
        assertThat(events.get(0).getPayloadJson()).contains("\"memoryCount\":1", "周报必须按风险优先排序");
    }

    @Test
    void startRunShouldLoadSessionMemoryFromCollaborationContext() {
        AgentMemory memory = new AgentMemory();
        memory.setId(10L);
        memory.setMemoryType("session_summary");
        memory.setMemoryScope("session");
        memory.setConfidence("confirmed");
        memory.setSummaryText("需求评审必须先输出验收标准");
        when(agentMemoryService.listConfirmedMemoriesForRuntime(100L, 10L, 1L, 77L, 200L, List.of("session"), 4))
                .thenReturn(List.of(memory));
        when(modelGateway.chat(any(ChatModelCommand.class))).thenReturn(ChatModelResponse.builder()
                .content("Mock response")
                .promptTokens(3)
                .completionTokens(4)
                .totalTokens(7)
                .modelCallLogId(78L)
                .build());
        JavaInProcessRuntimeGateway memoryGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                agentVersionService,
                modelGateway,
                knowledgeDocumentService,
                agentMemoryService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        memoryGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(130L)
                .inputText("{\"modelId\":1,\"prompt\":\"生成需求分析\",\"collaborationContext\":{\"sessionId\":77,\"stageCode\":\"requirement_analysis\"}}")
                .runtimeSnapshotJson("{\"memoryPolicy\":{\"enabled\":true,\"maxItems\":4,\"scopes\":[\"session\"]}}")
                .traceId("trace-1")
                .build());

        verify(agentMemoryService).listConfirmedMemoriesForRuntime(100L, 10L, 1L, 77L, 200L, List.of("session"), 4);
        List<RuntimeEvent> events = memoryGateway.listEvents(130L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("MEMORY_CONTEXT", "MODEL_CALL", "MODEL_RESULT");
        assertThat(events.get(0).getPayloadJson()).contains("\"memoryCount\":1", "需求评审必须先输出验收标准");
    }

    @Test
    void startRunShouldRetrieveKnowledgeAndCacheRuntimeEvents() {
        when(knowledgeDocumentService.retrieve(any(RetrieveKnowledgeCommand.class))).thenReturn(List.of(
                KnowledgeRetrieveResult.builder()
                        .chunkId(1L)
                        .documentId(10L)
                        .chunkIndex(0)
                        .chunkText("项目风险需要跟踪")
                        .sourceJson("{\"documentId\":10,\"chunkIndex\":0}")
                        .score(1)
                        .build()
        ));

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(101L)
                .inputText("{\"knowledgeBaseId\":1,\"query\":\"风险\",\"topK\":3}")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<RetrieveKnowledgeCommand> captor = ArgumentCaptor.forClass(RetrieveKnowledgeCommand.class);
        verify(knowledgeDocumentService).retrieve(captor.capture());
        RetrieveKnowledgeCommand command = captor.getValue();
        assertThat(command.getKnowledgeBaseId()).isEqualTo(1L);
        assertThat(command.getQuery()).isEqualTo("风险");
        assertThat(command.getTopK()).isEqualTo(3);
        List<RuntimeEvent> events = runtimeGateway.listEvents(101L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .containsExactly("KNOWLEDGE_RETRIEVE", "KNOWLEDGE_RESULT");
        assertThat(events.get(1).getPayloadJson()).contains("\"hitCount\":1");
    }

    @Test
    void startRunShouldExecuteProjectAssistantWorkflow() {
        when(knowledgeDocumentService.retrieve(any(RetrieveKnowledgeCommand.class))).thenReturn(List.of(
                KnowledgeRetrieveResult.builder()
                        .chunkId(1L)
                        .documentId(10L)
                        .chunkIndex(0)
                        .chunkText("本周完成需求评审，存在延期风险。")
                        .sourceJson("{\"documentId\":10,\"chunkIndex\":0}")
                        .score(1)
                        .sourceTitle("本周项目资料")
                        .sourceType("knowledge_base")
                        .snippet("本周完成需求评审，存在延期风险。")
                        .confidence("high")
                        .accessChecked(true)
                        .build()
        ));
        when(modelGateway.chat(any(ChatModelCommand.class))).thenReturn(ChatModelResponse.builder()
                .content("项目周报内容")
                .promptTokens(10)
                .completionTokens(8)
                .totalTokens(18)
                .modelCallLogId(77L)
                .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(102L)
                .inputText("{\"assistantTaskType\":\"weekly_report\",\"knowledgeBaseId\":1,\"modelId\":1,\"query\":\"延期风险\"}")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<ChatModelCommand> modelCaptor = ArgumentCaptor.forClass(ChatModelCommand.class);
        verify(modelGateway, org.mockito.Mockito.times(1)).chat(modelCaptor.capture());
        assertThat(modelCaptor.getValue().getPrompt()).contains("项目周报", "本周完成需求评审");
        List<RuntimeEvent> events = runtimeGateway.listEvents(102L);
        assertThat(nonStepEventTypes(events))
                .containsExactly(
                        "AGENT_LOOP_OBSERVED",
                        "ASSISTANT_PLAN_CREATED",
                        "KNOWLEDGE_RETRIEVE",
                        "KNOWLEDGE_RESULT",
                        "MODEL_CALL",
                        "MODEL_RESULT",
                        "ASSISTANT_ARTIFACT",
                        "ASSISTANT_TASK_COMPLETED"
                );
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .contains("STEP_STARTED", "STEP_COMPLETED");
        assertThat(events.stream().map(RuntimeEvent::getPayloadJson).toList())
                .anyMatch(payload -> payload.contains("\"loopPhase\":\"observe\""))
                .anyMatch(payload -> payload.contains("\"loopPhase\":\"plan\""))
                .anyMatch(payload -> payload.contains("\"loopPhase\":\"act\""))
                .anyMatch(payload -> payload.contains("\"loopPhase\":\"artifact\""))
                .anyMatch(payload -> payload.contains("\"loopPhase\":\"reflect\""));
        assertThat(events.stream()
                .filter(event -> event.getStepId() != null)
                .map(RuntimeEvent::getStepId)
                .distinct()
                .toList()).containsExactly(1L, 2L, 3L, 4L, 5L);
        RuntimeEvent artifact = events.stream()
                .filter(event -> "ASSISTANT_ARTIFACT".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(artifact.getStepId()).isEqualTo(4L);
        assertThat(artifact.getPayloadJson()).contains("\"artifactType\":\"weekly_report\"", "项目周报");
        assertThat(artifact.getPayloadJson()).contains(
                "\"knowledgeConfidence\":\"high\"",
                "\"lowConfidence\":false",
                "\"sourceRefs\""
        );
    }

    @Test
    void startRunShouldGenerateActionItemsArtifactForMeetingMinutes() {
        when(modelGateway.chat(any(ChatModelCommand.class))).thenReturn(ChatModelResponse.builder()
                .content("会议纪要内容")
                .promptTokens(10)
                .completionTokens(8)
                .totalTokens(18)
                .modelCallLogId(77L)
                .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(109L)
                .inputText("{\"assistantTaskType\":\"meeting_minutes\",\"modelId\":1,\"prompt\":\"根据会议纪要创建项目任务\"}")
                .traceId("trace-1")
                .build());

        List<RuntimeEvent> events = runtimeGateway.listEvents(109L);
        assertThat(events).extracting(RuntimeEvent::getEventType)
                .contains("ASSISTANT_ARTIFACT", "ASSISTANT_TASK_COMPLETED");
        RuntimeEvent artifact = events.stream()
                .filter(event -> "ASSISTANT_ARTIFACT".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(artifact.getPayloadJson()).contains("\"artifactType\":\"meeting_action_items\"", "会议行动项");
    }

    @Test
    void startRunShouldGenerateRiskListArtifactForRiskAnalysis() {
        when(modelGateway.chat(any(ChatModelCommand.class))).thenReturn(ChatModelResponse.builder()
                .content("风险分析内容")
                .promptTokens(10)
                .completionTokens(8)
                .totalTokens(18)
                .modelCallLogId(77L)
                .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(110L)
                .inputText("{\"assistantTaskType\":\"risk_analysis\",\"modelId\":1,\"prompt\":\"分析当前项目延期风险\"}")
                .traceId("trace-1")
                .build());

        RuntimeEvent artifact = runtimeGateway.listEvents(110L).stream()
                .filter(event -> "ASSISTANT_ARTIFACT".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(artifact.getPayloadJson()).contains("\"artifactType\":\"risk_analysis\"", "项目风险清单");
    }

    @Test
    void startRunShouldGenerateAgileArtifactFromOutputArtifactTypes() {
        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(111L)
                .inputText("{\"assistantTaskType\":\"agile_testing\",\"prompt\":\"Produce test report\",\"outputArtifactTypes\":[\"test_report\"]}")
                .traceId("trace-1")
                .build());

        RuntimeEvent artifact = runtimeGateway.listEvents(111L).stream()
                .filter(event -> "ASSISTANT_ARTIFACT".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(artifact.getPayloadJson()).contains(
                "\"artifactType\":\"test_report\"",
                "\"artifactName\":\"test report\"",
                "Stage: testing"
        );
    }

    @Test
    void startRunShouldSuspendProjectAssistantWorkflowWhenToolBlocked() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(66L)
                        .approvalRequestId(88L)
                        .toolType("cli")
                        .toolCode("controlled.cli.create-task")
                        .riskLevel("high")
                        .executorType("cli")
                        .errorMessage("高风险工具审批")
                        .build()
        );

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(103L)
                .inputText("{\"assistantTaskType\":\"weekly_report\",\"toolId\":10,\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());

        List<RuntimeEvent> events = runtimeGateway.listEvents(103L);
        assertThat(nonStepEventTypes(events))
                .containsExactly(
                        "AGENT_LOOP_OBSERVED",
                        "ASSISTANT_PLAN_CREATED",
                        "TOOL_CALL",
                        "TOOL_BLOCKED",
                        "ASSISTANT_TASK_SUSPENDED"
                );
        assertThat(events.stream()
                .filter(event -> "TOOL_BLOCKED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow()
                .getStepId()).isEqualTo(2L);
        assertThat(events.stream()
                .filter(event -> "TOOL_BLOCKED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow()
                .getPayloadJson()).contains("\"executorType\":\"cli\"", "\"riskLevel\":\"high\"");
        ArgumentCaptor<RuntimeCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(RuntimeCheckpoint.class);
        verify(runtimeCheckpointService).save(checkpointCaptor.capture());
        assertThat(checkpointCaptor.getValue().getCheckpointType()).isEqualTo("tool_call");
        assertThat(checkpointCaptor.getValue().getCheckpointStatus()).isEqualTo("suspended");
        assertThat(checkpointCaptor.getValue().getApprovalRequestId()).isEqualTo(88L);
    }

    @Test
    void resumeRunShouldExecuteBlockedToolAfterApproval() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class)))
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(66L)
                        .approvalRequestId(88L)
                        .toolType("cli")
                        .toolCode("controlled.cli.create-task")
                        .riskLevel("high")
                        .executorType("cli")
                        .errorMessage("高风险工具审批")
                        .build())
                .thenReturn(ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(67L)
                        .toolType("cli")
                        .toolCode("controlled.cli.create-task")
                        .riskLevel("high")
                        .executorType("cli")
                        .resultJson("{\"amount\":100}")
                        .build());

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(104L)
                .inputText("{\"assistantTaskType\":\"weekly_report\",\"toolId\":10,\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());
        runtimeGateway.submitApprovalResult(RunApprovalResultCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(104L)
                .approvalRequestId(88L)
                .approvalStatus("approved")
                .build());
        runtimeGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(104L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build());

        ArgumentCaptor<ExecuteToolCallCommand> captor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService, org.mockito.Mockito.times(2)).executeToolCall(captor.capture());
        assertThat(captor.getAllValues().get(0).getApprovalBypassed()).isFalse();
        assertThat(captor.getAllValues().get(1).getApprovalBypassed()).isTrue();
        List<RuntimeEvent> events = runtimeGateway.listEvents(104L);
        assertThat(nonStepEventTypes(events))
                .containsExactly(
                        "AGENT_LOOP_OBSERVED",
                        "ASSISTANT_PLAN_CREATED",
                        "TOOL_CALL",
                        "TOOL_BLOCKED",
                        "ASSISTANT_TASK_SUSPENDED",
                        "TOOL_CALL",
                        "TOOL_RESULT",
                        "ASSISTANT_ARTIFACT",
                        "ASSISTANT_TASK_COMPLETED"
                );
        assertThat(events.stream()
                .filter(event -> "TOOL_RESULT".equals(event.getEventType()))
                .findFirst()
                .orElseThrow()
                .getPayloadJson()).contains("\"executorType\":\"cli\"", "\"riskLevel\":\"high\"");
    }

    @Test
    void resumeRunShouldDenyInMemorySuspendedToolWhenVersionScopeChanges() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolId\":10,\"toolCode\":\"controlled.cli.create-task\"}]");
        AgentVersion changedVersion = new AgentVersion();
        changedVersion.setId(11L);
        changedVersion.setToolScopeJson("[{\"toolId\":22,\"toolCode\":\"controlled.cli.other\"}]");
        AgentVersionService scopedAgentVersionService = mock(AgentVersionService.class);
        when(scopedAgentVersionService.getVersion(11L)).thenReturn(version, changedVersion);
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(66L)
                        .approvalRequestId(88L)
                        .toolType("cli")
                        .toolCode("controlled.cli.create-task")
                        .riskLevel("high")
                        .executorType("cli")
                        .errorMessage("high risk tool requires approval")
                        .build()
        );
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                scopedAgentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(125L)
                .inputText("{\"assistantTaskType\":\"weekly_report\",\"toolId\":10,\"toolCode\":\"controlled.cli.create-task\",\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());
        scopedGateway.submitApprovalResult(RunApprovalResultCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(125L)
                .approvalRequestId(88L)
                .approvalStatus("approved")
                .build());
        scopedGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(125L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build());

        verify(toolConfigService, org.mockito.Mockito.times(1)).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(nonStepEventTypes(scopedGateway.listEvents(125L)))
                .containsExactly(
                        "AGENT_LOOP_OBSERVED",
                        "ASSISTANT_PLAN_CREATED",
                        "TOOL_CALL",
                        "TOOL_BLOCKED",
                        "ASSISTANT_TASK_SUSPENDED",
                        "TOOL_DENIED"
                );
    }

    @Test
    void resumeRunShouldLoadApprovedCheckpointWhenMemoryCheckpointIsMissing() {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setTenantId(100L);
        checkpoint.setTaskId(1L);
        checkpoint.setRunId(106L);
        checkpoint.setCheckpointType("tool_call");
        checkpoint.setCheckpointStatus("approved");
        checkpoint.setApprovalRequestId(88L);
        checkpoint.setPayloadJson("{\"userId\":200,\"agentId\":10,\"agentVersionId\":11,\"traceId\":\"trace-1\","
                + "\"toolId\":10,\"callPayloadJson\":\"{\\\"amount\\\":100}\","
                + "\"rootJson\":\"{\\\"assistantTaskType\\\":\\\"weekly_report\\\",\\\"toolId\\\":10,\\\"callPayloadJson\\\":{\\\"amount\\\":100}}\"}");
        when(runtimeCheckpointService.getOne(any())).thenReturn(checkpoint);
        when(runtimeCheckpointService.claimApprovedCheckpoint(100L, 106L, 88L)).thenReturn(true);
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(67L)
                        .resultJson("{\"amount\":100}")
                        .build()
        );

        runtimeGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(106L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build());

        ArgumentCaptor<ExecuteToolCallCommand> captor = ArgumentCaptor.forClass(ExecuteToolCallCommand.class);
        verify(toolConfigService).executeToolCall(captor.capture());
        assertThat(captor.getValue().getApprovalBypassed()).isTrue();
        assertThat(captor.getValue().getCallPayloadJson()).isEqualTo("{\"amount\":100}");
        assertThat(runtimeGateway.listEvents(106L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT", "ASSISTANT_ARTIFACT", "ASSISTANT_TASK_COMPLETED");
    }

    @Test
    void resumeRunShouldDenyPersistedCheckpointOutsideAgentVersionScope() {
        RuntimeCheckpoint checkpoint = approvedToolCheckpoint(124L);
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setToolScopeJson("[{\"toolId\":22,\"toolCode\":\"controlled.cli.allowed\"}]");
        AgentVersionService scopedAgentVersionService = mock(AgentVersionService.class);
        when(scopedAgentVersionService.getVersion(11L)).thenReturn(version);
        when(runtimeCheckpointService.getOne(any())).thenReturn(checkpoint);
        when(runtimeCheckpointService.claimApprovedCheckpoint(100L, 124L, 88L)).thenReturn(true);
        JavaInProcessRuntimeGateway scopedGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                scopedAgentVersionService,
                modelGateway,
                knowledgeDocumentService,
                runtimeCheckpointService,
                new com.xiaoai.agent.runtime.engine.AgentRunEngine(new ObjectMapper()),
                new ObjectMapper()
        );

        scopedGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(124L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build());

        verify(toolConfigService, org.mockito.Mockito.never()).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(scopedGateway.listEvents(124L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_DENIED");
        assertThat(scopedGateway.listEvents(124L).get(0).getPayloadJson()).contains(
                "\"approvalRequestId\":88",
                "\"reason\":\"tool_not_in_agent_version_scope_on_resume\""
        );
    }

    @Test
    void resumeRunShouldNotExecutePersistedCheckpointWhenClaimFails() {
        RuntimeCheckpoint checkpoint = approvedToolCheckpoint(107L);
        when(runtimeCheckpointService.getOne(any())).thenReturn(checkpoint);
        when(runtimeCheckpointService.claimApprovedCheckpoint(100L, 107L, 88L)).thenReturn(false);

        runtimeGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(107L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build());

        verify(toolConfigService, org.mockito.Mockito.never()).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(runtimeGateway.listEvents(107L)).isEmpty();
    }

    @Test
    void onlyOneRuntimeNodeShouldExecutePersistedCheckpointWhenClaimedConcurrently() {
        RuntimeCheckpoint checkpoint = approvedToolCheckpoint(108L);
        RuntimeCheckpointService sharedCheckpointService = mock(RuntimeCheckpointService.class);
        ToolConfigService nodeAToolConfigService = mock(ToolConfigService.class);
        ToolConfigService nodeBToolConfigService = mock(ToolConfigService.class);
        JavaInProcessRuntimeGateway nodeA = new JavaInProcessRuntimeGateway(
                nodeAToolConfigService,
                modelGateway,
                knowledgeDocumentService,
                sharedCheckpointService,
                new ObjectMapper()
        );
        JavaInProcessRuntimeGateway nodeB = new JavaInProcessRuntimeGateway(
                nodeBToolConfigService,
                modelGateway,
                knowledgeDocumentService,
                sharedCheckpointService,
                new ObjectMapper()
        );
        when(sharedCheckpointService.getOne(any())).thenReturn(checkpoint);
        when(sharedCheckpointService.claimApprovedCheckpoint(100L, 108L, 88L)).thenReturn(true, false);
        when(nodeAToolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("success")
                        .toolCallLogId(67L)
                        .resultJson("{\"amount\":100}")
                        .build()
        );

        RunResumeCommand resume = RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(108L)
                .resumePayloadJson("{\"approvalRequestId\":88}")
                .build();
        nodeA.resumeRun(resume);
        nodeB.resumeRun(resume);

        verify(nodeAToolConfigService).executeToolCall(any(ExecuteToolCallCommand.class));
        verify(nodeBToolConfigService, org.mockito.Mockito.never()).executeToolCall(any(ExecuteToolCallCommand.class));
        assertThat(nodeA.listEvents(108L)).extracting(RuntimeEvent::getEventType)
                .containsExactly("TOOL_CALL", "TOOL_RESULT", "ASSISTANT_ARTIFACT", "ASSISTANT_TASK_COMPLETED");
        assertThat(nodeB.listEvents(108L)).isEmpty();
    }

    @Test
    void resumeRunShouldNotExecuteBlockedToolWhenApprovalDoesNotMatch() {
        when(toolConfigService.executeToolCall(any(ExecuteToolCallCommand.class))).thenReturn(
                ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(66L)
                        .approvalRequestId(88L)
                        .errorMessage("高风险工具审批")
                        .build()
        );

        runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(105L)
                .inputText("{\"assistantTaskType\":\"weekly_report\",\"toolId\":10,\"callPayloadJson\":{\"amount\":100}}")
                .traceId("trace-1")
                .build());
        runtimeGateway.submitApprovalResult(RunApprovalResultCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(105L)
                .approvalRequestId(89L)
                .approvalStatus("approved")
                .build());
        runtimeGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(105L)
                .resumePayloadJson("{\"approvalRequestId\":89}")
                .build());

        verify(toolConfigService, org.mockito.Mockito.times(1)).executeToolCall(any(ExecuteToolCallCommand.class));
        List<RuntimeEvent> events = runtimeGateway.listEvents(105L);
        assertThat(nonStepEventTypes(events))
                .containsExactly(
                        "AGENT_LOOP_OBSERVED",
                        "ASSISTANT_PLAN_CREATED",
                        "TOOL_CALL",
                        "TOOL_BLOCKED",
                        "ASSISTANT_TASK_SUSPENDED"
                );
    }

    private List<String> nonStepEventTypes(List<RuntimeEvent> events) {
        return events.stream()
                .map(RuntimeEvent::getEventType)
                .filter(eventType -> !"STEP_STARTED".equals(eventType) && !"STEP_COMPLETED".equals(eventType))
                .toList();
    }

    private RuntimeCheckpoint approvedToolCheckpoint(Long runId) {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setTenantId(100L);
        checkpoint.setTaskId(1L);
        checkpoint.setRunId(runId);
        checkpoint.setCheckpointType("tool_call");
        checkpoint.setCheckpointStatus("approved");
        checkpoint.setApprovalRequestId(88L);
        checkpoint.setPayloadJson("{\"userId\":200,\"agentId\":10,\"agentVersionId\":11,\"traceId\":\"trace-1\","
                + "\"toolId\":10,\"callPayloadJson\":\"{\\\"amount\\\":100}\","
                + "\"rootJson\":\"{\\\"assistantTaskType\\\":\\\"weekly_report\\\",\\\"toolId\\\":10,\\\"callPayloadJson\\\":{\\\"amount\\\":100}}\"}");
        return checkpoint;
    }
}
