package com.xiaoai.agent.collaboration.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.service.AgentHandoffService;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.collaboration.service.QualityGateService;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategy;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.model.StartTaskCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OrchestratedTeamStrategy implements CollaborationStrategy {

    private static final Logger log = LoggerFactory.getLogger(OrchestratedTeamStrategy.class);

    private final CollaborationPlanService collaborationPlanService;
    private final AgentThreadService agentThreadService;
    private final AgentHandoffService agentHandoffService;
    private final QualityGateService qualityGateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrchestratedTeamStrategy(CollaborationPlanService collaborationPlanService,
                                    AgentThreadService agentThreadService,
                                    AgentHandoffService agentHandoffService,
                                    QualityGateService qualityGateService) {
        this.collaborationPlanService = collaborationPlanService;
        this.agentThreadService = agentThreadService;
        this.agentHandoffService = agentHandoffService;
        this.qualityGateService = qualityGateService;
    }

    @Override
public String strategyType() {
        return "orchestrated_team";
    }

    @Override
public CollaborationStrategyResult start(CollaborationStrategyContext context) {
        CollaborationPlan plan = collaborationPlanService.getPlan(context.getPlanId());
        if (!"passed".equals(plan.getValidationStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration plan must be validated before strategy start");
        }
        JsonNode root = readPlan(plan.getPlanJson());
        JsonNode stages = root.path("stages");
        if (!stages.isArray() || stages.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration plan stages is required");
        }
        JsonNode firstStage = stages.get(0);
        int createdThreadCount = createFirstThreadIfAbsent(context.getSessionId(), firstStage);
        int createdGateCount = createRequiredGates(context.getSessionId(), stages);
        return CollaborationStrategyResult.started(createdThreadCount, createdGateCount, stageCode(firstStage));
    }

    @Override
public CollaborationStrategyResult continueAfterGate(CollaborationStrategyContext context) {
        if (qualityGateService.hasBlockingFailedGate(context.getSessionId())) {
            return CollaborationStrategyResult.blocked("Required quality gate failed");
        }
        CollaborationPlan plan = collaborationPlanService.getPlan(context.getPlanId());
        JsonNode root = readPlan(plan.getPlanJson());
        JsonNode stages = root.path("stages");
        if (!stages.isArray() || context.getGateCode() == null) {
            return CollaborationStrategyResult.continued(0);
        }
        int currentStageIndex = findCurrentStageIndexByGate(context.getSessionId(), stages, context.getGateCode());
        if (currentStageIndex < 0) {
            return CollaborationStrategyResult.continued(0);
        }
        if (currentStageIndex + 1 >= stages.size()) {
            return CollaborationStrategyResult.completed(stageCode(stages.get(currentStageIndex)));
        }
        Set<String> existingThreadNames = safeList(agentThreadService.listThreads(context.getSessionId())).stream()
                .map(AgentThread::getThreadName)
                .collect(Collectors.toSet());
        InheritedArtifact inheritedArtifact = inheritedArtifact(context.getSessionId(), stages.get(currentStageIndex));
        String fanOutGateCode = gateCode(stages.get(currentStageIndex + 1));
        int createdThreadCount = 0;
        String firstCreatedStageCode = null;
        for (int index = currentStageIndex + 1; index < stages.size(); index++) {
            JsonNode nextStage = stages.get(index);
            if (index > currentStageIndex + 1 && !sameGateCode(fanOutGateCode, gateCode(nextStage))) {
                break;
            }
            String nextThreadName = stageThreadName(nextStage);
            if (existingThreadNames.contains(nextThreadName)) {
                continue;
            }
            List<InheritedArtifact> inputArtifacts = inheritedArtifactsForStage(context.getSessionId(), nextStage, inheritedArtifact);
            AgentThreadResponse nextThread = createStageThread(context.getSessionId(), root, nextStage, inheritedArtifact.artifactId());
            existingThreadNames.add(nextThreadName);
            createdThreadCount++;
            if (firstCreatedStageCode == null) {
                firstCreatedStageCode = stageCode(nextStage);
            }
            tryCreateArtifactHandoffsIfAbsent(context.getSessionId(), inputArtifacts, nextThread);
            autoStartStageTaskIfRequested(context.getSessionId(), root, nextStage, nextThread, artifactIds(inputArtifacts));
        }
        return CollaborationStrategyResult.continued(createdThreadCount, firstCreatedStageCode);
    }

    private AgentThreadResponse createStageThread(Long sessionId, JsonNode stage) {
        return createStageThread(sessionId, null, stage, null);
    }

    private AgentThreadResponse createStageThread(Long sessionId, JsonNode root, JsonNode stage, Long inheritedArtifactId) {
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(sessionId);
        command.setAgentId(requiredLong(stage, "agentId"));
        command.setAgentVersionId(optionalLong(stage, "agentVersionId"));
        command.setRoleId(optionalLong(stage, "roleId"));
        command.setThreadName(stageThreadName(stage));
        List<Long> inheritedArtifactIds = inheritedArtifactIdsForStage(sessionId, stage);
        Long primaryInheritedArtifactId = firstNonNull(inheritedArtifactId, inheritedArtifactIds.isEmpty() ? null : inheritedArtifactIds.get(0));
        command.setInputArtifactId(firstNonNull(optionalLong(stage, "inputArtifactId"), primaryInheritedArtifactId));
        if (!inheritedArtifactIds.isEmpty()) {
            command.setInputArtifactIds(inheritedArtifactIds);
        } else if (primaryInheritedArtifactId != null) {
            command.setInputArtifactIds(List.of(primaryInheritedArtifactId));
        }
        command.setOutputArtifactTypes(textArray(stage.path("outputArtifactTypes")));
        command.setInputArtifactVersion(optionalLong(stage, "inputArtifactVersion"));
        command.setStageCode(stageCode(stage));
        command.setCreateTask(stage.path("createTask").asBoolean(false));
        command.setInputText(firstNonNull(textOrNull(stage, "inputText"), inheritedArtifactId == null ? null : "Continue with Artifact #" + inheritedArtifactId));
        command.setToolCallsJson(stage.path("toolCalls").isArray() ? stage.path("toolCalls").toString() : null);
        command.setRequireAcceptedInputHandoff(primaryInheritedArtifactId != null && requiresAcceptedInputHandoff(root, stage));
        return agentThreadService.createThread(command);
    }

    private void autoStartStageTaskIfRequested(Long sessionId,
                                               JsonNode root,
                                               JsonNode stage,
                                               AgentThreadResponse thread,
                                               List<Long> inheritedArtifactIds) {
        if (!stage.path("autoStartTask").asBoolean(false) || thread == null || thread.getThreadId() == null || thread.getTaskId() == null) {
            return;
        }
        if (!safeList(inheritedArtifactIds).isEmpty()
                && requiresAcceptedInputHandoff(root, stage)
                && !hasAcceptedArtifactHandoffs(sessionId, thread.getThreadId(), inheritedArtifactIds)) {
            return;
        }
        agentThreadService.startThreadTask(sessionId, thread.getThreadId(), new StartTaskCommand());
    }

    private int createFirstThreadIfAbsent(Long sessionId, JsonNode stage) {
        String firstThreadName = stageThreadName(stage);
        boolean existing = safeList(agentThreadService.listThreads(sessionId)).stream()
                .map(AgentThread::getThreadName)
                .anyMatch(firstThreadName::equals);
        if (existing) {
            return 0;
        }
        AgentThreadResponse thread = createStageThread(sessionId, stage);
        autoStartStageTaskIfRequested(sessionId, null, stage, thread, List.of());
        return 1;
    }

    private int createRequiredGates(Long sessionId, JsonNode stages) {
        int createdGateCount = 0;
        Set<String> existingGateCodes = safeList(qualityGateService.listGates(sessionId)).stream()
                .map(gate -> gate.getGateCode() == null ? "" : gate.getGateCode())
                .collect(Collectors.toSet());
        for (JsonNode stage : stages) {
            String gateCode = stage.path("requiresGate").asText(null);
            if (gateCode == null || gateCode.isBlank()) {
                continue;
            }
            if (existingGateCodes.contains(gateCode)) {
                continue;
            }
            CreateQualityGateCommand command = new CreateQualityGateCommand();
            command.setSessionId(sessionId);
            command.setGateCode(gateCode);
            command.setGateName(textOrDefault(stage, "gateName", gateCode));
            command.setGateType(textOrDefault(stage, "gateType", "manual_confirmation"));
            command.setRequired(true);
            command.setRuleJson("{}");
            qualityGateService.createGate(command);
            existingGateCodes.add(gateCode);
            createdGateCount++;
        }
        return createdGateCount;
    }

    private JsonNode readPlan(String planJson) {
        try {
            return objectMapper.readTree(planJson);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration plan JSON is invalid");
        }
    }

    private Long requiredLong(JsonNode node, String fieldName) {
        Long value = optionalLong(node, fieldName);
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration plan stage missing " + fieldName);
        }
        return value;
    }

    private Long optionalLong(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String stageThreadName(JsonNode stage) {
        return textOrDefault(stage, "stageName", stage.path("stageCode").asText("collaboration_stage"));
    }

    private String stageCode(JsonNode stage) {
        return textOrDefault(stage, "stageCode", stageThreadName(stage));
    }

    private String gateCode(JsonNode stage) {
        String value = stage.path("requiresGate").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private boolean sameGateCode(String first, String second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.equals(second);
    }

    private int findCurrentStageIndexByGate(Long sessionId, JsonNode stages, String gateCode) {
        Set<String> existingThreadNames = safeList(agentThreadService.listThreads(sessionId)).stream()
                .map(AgentThread::getThreadName)
                .collect(Collectors.toSet());
        int fallbackIndex = -1;
        int lastExistingIndex = -1;
        for (int index = 0; index < stages.size(); index++) {
            if (gateCode.equals(stages.get(index).path("requiresGate").asText(null))) {
                fallbackIndex = index;
                if (existingThreadNames.contains(stageThreadName(stages.get(index)))) {
                    lastExistingIndex = index;
                }
            }
        }
        return lastExistingIndex >= 0 ? lastExistingIndex : fallbackIndex;
    }

    private InheritedArtifact inheritedArtifact(Long sessionId, JsonNode currentStage) {
        String currentThreadName = stageThreadName(currentStage);
        return safeList(agentThreadService.listThreads(sessionId)).stream()
                .filter(thread -> currentThreadName.equals(thread.getThreadName()))
                .filter(thread -> thread.getOutputArtifactId() != null)
                .findFirst()
                .map(thread -> new InheritedArtifact(thread.getOutputArtifactId(), thread.getId()))
                .orElse(InheritedArtifact.empty());
    }

    private List<Long> inheritedArtifactIdsForStage(Long sessionId, JsonNode stage) {
        return inheritedArtifactsForStage(sessionId, stage).stream()
                .map(InheritedArtifact::artifactId)
                .toList();
    }

    private List<InheritedArtifact> inheritedArtifactsForStage(Long sessionId, JsonNode stage, InheritedArtifact fallback) {
        List<InheritedArtifact> artifacts = inheritedArtifactsForStage(sessionId, stage);
        if (!artifacts.isEmpty() || fallback.artifactId() == null) {
            return artifacts;
        }
        return List.of(fallback);
    }

    private List<InheritedArtifact> inheritedArtifactsForStage(Long sessionId, JsonNode stage) {
        JsonNode inputArtifactTypes = stage.path("inputArtifactTypes");
        if (!inputArtifactTypes.isArray() || inputArtifactTypes.isEmpty()) {
            return List.of();
        }
        Set<String> requiredTypes = new java.util.HashSet<>();
        inputArtifactTypes.forEach(type -> {
            String value = type.asText(null);
            if (value != null && !value.isBlank()) {
                requiredTypes.add(value);
            }
        });
        if (requiredTypes.isEmpty()) {
            return List.of();
        }
        return safeList(agentThreadService.listThreads(sessionId)).stream()
                .filter(thread -> thread.getOutputArtifactId() != null)
                .filter(thread -> stageProducesAnyRequiredType(threadStageCode(thread), stage.path("stageCode").asText(null), requiredTypes))
                .map(thread -> new InheritedArtifact(thread.getOutputArtifactId(), thread.getId()))
                .filter(distinctByArtifactId())
                .toList();
    }

    private String threadStageCode(AgentThread thread) {
        if (thread.getContextJson() != null && !thread.getContextJson().isBlank()) {
            try {
                String stageCode = objectMapper.readTree(thread.getContextJson()).path("stageCode").asText(null);
                if (stageCode != null && !stageCode.isBlank()) {
                    return stageCode;
                }
            } catch (Exception ignored) {
                // Fall back to thread name for older rows without stageCode in context.
            }
        }
        return thread.getThreadName() == null ? null : thread.getThreadName().trim().toLowerCase().replace(' ', '_');
    }

    private boolean stageProducesAnyRequiredType(String producerStageCode, String targetStageCode, Set<String> requiredTypes) {
        if (producerStageCode == null || producerStageCode.isBlank() || producerStageCode.equals(targetStageCode)) {
            return false;
        }
        return (requiredTypes.contains("implementation_summary") && producerStageCode.endsWith("_implementation"))
                || (requiredTypes.contains("technical_design") && "technical_design".equals(producerStageCode))
                || (requiredTypes.contains("test_report") && "testing".equals(producerStageCode));
    }

    private boolean tryCreateArtifactHandoffIfAbsent(Long sessionId,
                                                     InheritedArtifact inheritedArtifact,
                                                     AgentThreadResponse nextThread) {
        if (inheritedArtifact.artifactId() == null || nextThread == null || nextThread.getThreadId() == null) {
            return false;
        }
        try {
            boolean existing = agentHandoffService.listHandoffs(sessionId).stream()
                    .anyMatch(handoff -> inheritedArtifact.artifactId().equals(handoff.getArtifactId())
                            && nextThread.getThreadId().equals(handoff.getToThreadId()));
            if (existing) {
                return false;
            }
            CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
            command.setSessionId(sessionId);
            command.setFromThreadId(inheritedArtifact.fromThreadId());
            command.setToThreadId(nextThread.getThreadId());
            command.setArtifactId(inheritedArtifact.artifactId());
            command.setHandoffType("artifact");
            command.setMessageText("Inherited Artifact #" + inheritedArtifact.artifactId());
            agentHandoffService.createHandoff(command);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to create artifact handoff for sessionId={}, artifactId={}, nextThreadId={}",
                    sessionId, inheritedArtifact.artifactId(), nextThread.getThreadId(), exception);
            return false;
        }
    }

    private void tryCreateArtifactHandoffsIfAbsent(Long sessionId,
                                                   List<InheritedArtifact> inheritedArtifacts,
                                                   AgentThreadResponse nextThread) {
        safeList(inheritedArtifacts).forEach(artifact -> tryCreateArtifactHandoffIfAbsent(sessionId, artifact, nextThread));
    }

    private boolean requiresAcceptedInputHandoff(JsonNode root, JsonNode stage) {
        JsonNode value = stage.get("requireAcceptedInputHandoff");
        if (value == null || value.isNull()) {
            value = stage.get("requireAcceptedHandoff");
        }
        if (value == null || value.isNull()) {
            value = stage.get("waitForHandoffAcceptance");
        }
        if (value == null || value.isNull()) {
            JsonNode policy = root == null ? null : root.path("handoffPolicy");
            value = policy == null || policy.isMissingNode() ? null : policy.get("requireAcceptedBeforeConsume");
        }
        if (value == null || value.isNull()) {
            JsonNode policy = root == null ? null : root.path("handoffPolicy");
            String mode = policy == null || policy.isMissingNode() ? "" : policy.path("mode").asText("");
            return "strict".equalsIgnoreCase(mode);
        }
        return value != null && value.asBoolean(false);
    }

    private boolean hasAcceptedArtifactHandoff(Long sessionId, Long toThreadId, Long artifactId) {
        return agentHandoffService.listHandoffs(sessionId).stream()
                .anyMatch(handoff -> artifactId.equals(handoff.getArtifactId())
                        && toThreadId.equals(handoff.getToThreadId())
                        && "artifact".equals(handoff.getHandoffType())
                        && "accepted".equals(handoff.getStatus()));
    }

    private boolean hasAcceptedArtifactHandoffs(Long sessionId, Long toThreadId, List<Long> artifactIds) {
        return safeList(artifactIds).stream()
                .allMatch(artifactId -> hasAcceptedArtifactHandoff(sessionId, toThreadId, artifactId));
    }

    private List<Long> artifactIds(List<InheritedArtifact> artifacts) {
        return safeList(artifacts).stream()
                .map(InheritedArtifact::artifactId)
                .toList();
    }

    private java.util.function.Predicate<InheritedArtifact> distinctByArtifactId() {
        Set<Long> seen = new java.util.HashSet<>();
        return artifact -> seen.add(artifact.artifactId());
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> textArray(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            String value = item.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        });
        return values;
    }

    private record InheritedArtifact(Long artifactId, Long fromThreadId) {

        private static InheritedArtifact empty() {
            return new InheritedArtifact(null, null);
        }
    }
}
