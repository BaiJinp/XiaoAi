package com.xiaoai.agent.runtime.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.runtime.engine.AgentRunEngine;
import com.xiaoai.agent.runtime.model.RunApprovalResultCommand;
import com.xiaoai.agent.runtime.model.RunCancelCommand;
import com.xiaoai.agent.runtime.model.RunResumeCommand;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import com.xiaoai.agent.runtime.model.RunUserInputCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import com.xiaoai.agent.safety.PromptSafetyValidator;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JavaInProcessRuntimeGateway implements RuntimeGateway {

    private final Map<Long, List<RuntimeEvent>> eventCache = new ConcurrentHashMap<>();

    public JavaInProcessRuntimeGateway() {
    }

    public JavaInProcessRuntimeGateway(ToolConfigService toolConfigService,
                                       AgentVersionService agentVersionService,
                                       ModelGateway modelGateway,
                                       KnowledgeDocumentService knowledgeDocumentService,
                                       AgentMemoryService agentMemoryService,
                                       RuntimeCheckpointService runtimeCheckpointService,
                                       AgentRunEngine agentRunEngine,
                                       PromptSafetyValidator promptSafetyValidator,
                                       ObjectMapper objectMapper) {
    }

    public JavaInProcessRuntimeGateway(ToolConfigService toolConfigService,
                                       AgentVersionService agentVersionService,
                                       ModelGateway modelGateway,
                                       KnowledgeDocumentService knowledgeDocumentService,
                                       RuntimeCheckpointService runtimeCheckpointService,
                                       AgentRunEngine agentRunEngine,
                                       ObjectMapper objectMapper) {
        this(toolConfigService, agentVersionService, modelGateway, knowledgeDocumentService, null,
                runtimeCheckpointService, agentRunEngine, null, objectMapper);
    }

    public JavaInProcessRuntimeGateway(ToolConfigService toolConfigService,
                                       ModelGateway modelGateway,
                                       KnowledgeDocumentService knowledgeDocumentService,
                                       RuntimeCheckpointService runtimeCheckpointService,
                                       ObjectMapper objectMapper) {
        this(toolConfigService, null, modelGateway, knowledgeDocumentService, runtimeCheckpointService,
                new AgentRunEngine(objectMapper), objectMapper);
    }

    @Override
    public RunStartResult startRun(RunStartCommand command) {
        record(command, null, "RUN_STARTED", "Runtime run started", "{}");
        record(command, null, "RUN_ACCEPTED", "Runtime run accepted", "{}");
        return RunStartResult.builder()
                .tenantId(command.getTenantId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .runtimeType("java_in_process")
                .accepted(true)
                .build();
    }

    @Override
    public void cancelRun(RunCancelCommand command) {
        record(command.getTenantId(), null, null, command.getRunId(), null,
                "RUN_CANCELLED", "Runtime run cancelled", "{}");
    }

    @Override
    public void resumeRun(RunResumeCommand command) {
        record(command.getTenantId(), null, null, command.getRunId(), null,
                "RUN_RESUMED", "Runtime run resumed", "{}");
    }

    @Override
    public void submitUserInput(RunUserInputCommand command) {
        record(command.getTenantId(), null, null, command.getRunId(), null,
                "USER_INPUT_SUBMITTED", "User input submitted", "{}");
    }

    @Override
    public void submitApprovalResult(RunApprovalResultCommand command) {
        record(command.getTenantId(), null, null, command.getRunId(), null,
                "APPROVAL_RESULT_SUBMITTED", "Approval result submitted", "{}");
    }

    @Override
    public List<RuntimeEvent> listEvents(Long runId) {
        return eventCache.getOrDefault(runId, List.of());
    }

    private void record(RunStartCommand command, Long stepId, String eventType, String summary, String payloadJson) {
        record(command.getTenantId(), command.getUserId(), command.getAgentId(), command.getRunId(), stepId,
                eventType, summary, payloadJson);
    }

    private void record(Long tenantId, Long userId, Long agentId, Long runId, Long stepId,
                        String eventType, String summary, String payloadJson) {
        if (runId == null) {
            return;
        }
        RuntimeEvent event = RuntimeEvent.builder()
                .tenantId(tenantId)
                .userId(userId)
                .agentId(agentId)
                .runId(runId)
                .stepId(stepId)
                .eventType(eventType)
                .eventSummary(summary)
                .payloadJson(payloadJson)
                .occurredAt(OffsetDateTime.now())
                .build();
        eventCache.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(event);
    }
}
