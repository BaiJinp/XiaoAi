package com.xiaoai.agent.collaboration.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.collaboration.service.CollaborationGateAdvanceService;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategy;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyRegistry;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.task.model.StartTaskCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollaborationGateAdvanceServiceImpl implements CollaborationGateAdvanceService {

    private final CollaborationSessionService collaborationSessionService;
    private final CollaborationPlanService collaborationPlanService;
    private final AgentThreadService agentThreadService;
    private final CollaborationStrategyRegistry collaborationStrategyRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
public void continueAfterGate(Long sessionId, QualityGate gate) {
        if (gate == null || !sessionId.equals(gate.getSessionId())) {
            return;
        }
        CollaborationSession session = collaborationSessionService.getSession(sessionId);
        if (session == null) {
            return;
        }
        Long activePlanId = activePlanId(session.getContextJson());
        if (activePlanId == null) {
            return;
        }
        CollaborationStrategy strategy = collaborationStrategyRegistry.getStrategy(session.getStrategyType());
        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(sessionId)
                .planId(activePlanId)
                .gateCode(gate.getGateCode())
                .build());
        boolean sessionChanged = false;
        if (result.getCurrentStageCode() != null && !result.getCurrentStageCode().isBlank()) {
            session.setCurrentStageCode(result.getCurrentStageCode());
            sessionChanged = true;
        }
        if ("completed".equals(result.getStatus())) {
            session.setStatus("completed");
            sessionChanged = true;
        }
        if (sessionChanged) {
            collaborationSessionService.updateById(session);
        }
    }

    @Override
public void failAfterGate(Long sessionId, QualityGate gate) {
        if (gate == null || !sessionId.equals(gate.getSessionId()) || !Boolean.TRUE.equals(gate.getRequired())) {
            return;
        }
        CollaborationSession session = collaborationSessionService.getSession(sessionId);
        if (session == null) {
            return;
        }
        Long activePlanId = activePlanId(session.getContextJson());
        JsonNode failedStage = null;
        JsonNode reworkStage = null;
        if (activePlanId != null) {
            JsonNode stages = readStages(activePlanId);
            failedStage = findStageByGate(stages, gate.getGateCode());
            reworkStage = findReworkStage(stages, failedStage);
        }
        session.setStatus("blocked");
        String failedStageCode = textOrNull(failedStage, "stageCode");
        if (failedStageCode != null) {
            session.setCurrentStageCode(failedStageCode);
        }
        collaborationSessionService.updateById(session);
        createReworkThreadIfConfigured(sessionId, gate, reworkStage, failedStageCode);
    }

    private Long activePlanId(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return null;
        }
        try {
            JsonNode activePlanId = objectMapper.readTree(contextJson).path("activePlanId");
            return activePlanId.canConvertToLong() ? activePlanId.asLong() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode readStages(Long planId) {
        if (planId == null) {
            return null;
        }
        try {
            CollaborationPlan plan = collaborationPlanService.getPlan(planId);
            return objectMapper.readTree(plan.getPlanJson()).path("stages");
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode findStageByGate(JsonNode stages, String gateCode) {
        if (stages == null || !stages.isArray() || gateCode == null || gateCode.isBlank()) {
            return null;
        }
        for (JsonNode stage : stages) {
            if (gateCode.equals(stage.path("requiresGate").asText(null))) {
                return stage;
            }
        }
        return null;
    }

    private JsonNode findReworkStage(JsonNode stages, JsonNode failedStage) {
        String reworkStageCode = textOrNull(failedStage, "reworkStageCode");
        if (stages == null || !stages.isArray() || reworkStageCode == null) {
            return null;
        }
        for (JsonNode stage : stages) {
            if (reworkStageCode.equals(stage.path("stageCode").asText(null))) {
                return stage;
            }
        }
        return null;
    }

    private void createReworkThreadIfConfigured(Long sessionId, QualityGate gate, JsonNode reworkStage, String failedStageCode) {
        if (reworkStage == null || !reworkStage.isObject() || !reworkStage.hasNonNull("agentId")) {
            return;
        }
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(sessionId);
        command.setAgentId(reworkStage.path("agentId").asLong());
        command.setAgentVersionId(optionalLong(reworkStage, "agentVersionId"));
        command.setRoleId(optionalLong(reworkStage, "roleId"));
        command.setThreadName(textOrDefault(reworkStage, "stageName", textOrDefault(reworkStage, "stageCode", "rework")));
        boolean existing = agentThreadService.listThreads(sessionId).stream()
                .anyMatch(thread -> command.getThreadName().equals(thread.getThreadName()));
        if (existing) {
            return;
        }
        command.setInputArtifactId(optionalLong(reworkStage, "inputArtifactId"));
        command.setStageCode(textOrNull(reworkStage, "stageCode"));
        command.setCreateTask(reworkStage.path("createTask").asBoolean(false));
        command.setInputText(textOrDefault(reworkStage, "inputText",
                "Rework required after gate " + gate.getGateCode()
                        + (failedStageCode == null ? "" : " for stage " + failedStageCode)
                        + (gate.getFailReason() == null || gate.getFailReason().isBlank() ? "" : ": " + gate.getFailReason())));
        command.setToolCallsJson(reworkStage.path("toolCalls").isArray() ? reworkStage.path("toolCalls").toString() : null);
        AgentThreadResponse thread = agentThreadService.createThread(command);
        autoStartReworkTaskIfRequested(sessionId, reworkStage, thread);
    }

    private void autoStartReworkTaskIfRequested(Long sessionId, JsonNode reworkStage, AgentThreadResponse thread) {
        if (!reworkStage.path("autoStartTask").asBoolean(false)
                || thread == null
                || thread.getThreadId() == null
                || thread.getTaskId() == null) {
            return;
        }
        agentThreadService.startThreadTask(sessionId, thread.getThreadId(), new StartTaskCommand());
    }

    private Long optionalLong(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        String value = textOrNull(node, fieldName);
        return value == null ? defaultValue : value;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
