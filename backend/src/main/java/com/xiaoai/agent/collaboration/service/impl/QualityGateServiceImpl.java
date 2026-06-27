package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.mapper.AgentThreadMapper;
import com.xiaoai.agent.collaboration.mapper.QualityGateMapper;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.model.UpdateQualityGateCommand;
import com.xiaoai.agent.collaboration.service.QualityGateService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.service.TaskArtifactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QualityGateServiceImpl extends ServiceImpl<QualityGateMapper, QualityGate> implements QualityGateService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentThreadMapper agentThreadMapper;
    private TaskArtifactService taskArtifactService;

    @Autowired(required = false)
public void setQualitySnapshotDependencies(AgentThreadMapper agentThreadMapper,
                                               TaskArtifactService taskArtifactService) {
        this.agentThreadMapper = agentThreadMapper;
        this.taskArtifactService = taskArtifactService;
    }

    @Override
public QualityGate createGate(CreateQualityGateCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        QualityGate gate = new QualityGate();
        gate.setTenantId(tenantId);
        gate.setSessionId(command.getSessionId());
        gate.setGateCode(command.getGateCode());
        gate.setGateName(command.getGateName());
        gate.setGateType(command.getGateType());
        gate.setStatus("pending");
        gate.setRequired(command.getRequired() == null || command.getRequired());
        gate.setRuleJson(command.getRuleJson() == null || command.getRuleJson().isBlank() ? "{}" : command.getRuleJson());
        gate.setResultJson("{}");
        save(gate);
        return gate;
    }

    @Override
public List<QualityGate> listGates(Long sessionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return getBaseMapper().selectList(new LambdaQueryWrapper<QualityGate>()
                .eq(QualityGate::getTenantId, tenantId)
                .eq(QualityGate::getSessionId, sessionId)
                .orderByAsc(QualityGate::getCreatedAt));
    }

    @Override
public QualityGate passGate(Long sessionId, Long gateId, UpdateQualityGateCommand command) {
        QualityGate gate = getGate(gateId);
        ensureGateBelongsToSession(sessionId, gate);
        ensureGateReadyToPass(gate);
        gate.setStatus("passed");
        gate.setResultJson(withQualitySnapshot(command.getResultJson(), gate));
        gate.setFailReason(null);
        gate.setPassedAt(OffsetDateTime.now());
        updateById(gate);
        return gate;
    }

    @Override
public QualityGate failGate(Long sessionId, Long gateId, UpdateQualityGateCommand command) {
        QualityGate gate = getGate(gateId);
        ensureGateBelongsToSession(sessionId, gate);
        gate.setStatus("failed");
        gate.setResultJson(defaultJson(command.getResultJson()));
        gate.setFailReason(command.getFailReason());
        gate.setPassedAt(null);
        updateById(gate);
        return gate;
    }

    @Override
public boolean hasBlockingFailedGate(Long sessionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        List<QualityGate> failedGates = getBaseMapper().selectList(new LambdaQueryWrapper<QualityGate>()
                .eq(QualityGate::getTenantId, tenantId)
                .eq(QualityGate::getSessionId, sessionId)
                .eq(QualityGate::getStatus, "failed"));
        return failedGates.stream().anyMatch(gate -> Boolean.TRUE.equals(gate.getRequired()));
    }

    @Override
public QualityGate getGate(Long gateId) {
        Long tenantId = UserContextHolder.requireTenantId();
        QualityGate gate = getBaseMapper().selectOne(new LambdaQueryWrapper<QualityGate>()
                .eq(QualityGate::getTenantId, tenantId)
                .eq(QualityGate::getId, gateId)
                .last("limit 1"));
        if (gate == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Quality gate not found");
        }
        return gate;
    }

    private String defaultJson(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private void ensureGateBelongsToSession(Long sessionId, QualityGate gate) {
        if (gate == null || sessionId == null || !sessionId.equals(gate.getSessionId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Quality gate must belong to session");
        }
    }

    private void ensureGateReadyToPass(QualityGate gate) {
        Set<String> targetStages = targetStagesForGate(gate.getGateCode());
        if (targetStages.isEmpty() || agentThreadMapper == null) {
            return;
        }
        Long tenantId = UserContextHolder.requireTenantId();
        List<AgentThread> threads = agentThreadMapper.selectList(new LambdaQueryWrapper<AgentThread>()
                .eq(AgentThread::getTenantId, tenantId)
                .eq(AgentThread::getSessionId, gate.getSessionId()));
        for (String targetStage : targetStages) {
            AgentThread thread = threads.stream()
                    .filter(item -> targetStage.equals(readStageCode(item.getContextJson())))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                            "Quality gate target stage is not created: " + targetStage));
            if (!"completed".equals(thread.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Quality gate target stage is not completed: " + targetStage);
            }
            if (thread.getOutputArtifactId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Quality gate target stage has no output artifact: " + targetStage);
            }
        }
    }

    private String withQualitySnapshot(String json, QualityGate gate) {
        ObjectNode result = parseObjectJson(defaultJson(json));
        result.set("quality", buildQualitySnapshot(gate));
        return result.toString();
    }

    private ObjectNode parseObjectJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.set("submittedResult", node);
            return result;
        } catch (Exception ignored) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("submittedResultJson", json);
            return result;
        }
    }

    private ObjectNode buildQualitySnapshot(QualityGate gate) {
        ObjectNode quality = objectMapper.createObjectNode();
        ArrayNode warnings = objectMapper.createArrayNode();
        ArrayNode artifacts = objectMapper.createArrayNode();
        quality.put("checked", true);

        if (agentThreadMapper == null || taskArtifactService == null) {
            warnings.add("quality snapshot dependencies are unavailable");
            quality.put("status", "warning");
            quality.put("artifactCount", 0);
            quality.set("warnings", warnings);
            quality.set("artifacts", artifacts);
            return quality;
        }

        Long tenantId = UserContextHolder.requireTenantId();
        List<AgentThread> threads = agentThreadMapper.selectList(new LambdaQueryWrapper<AgentThread>()
                .eq(AgentThread::getTenantId, tenantId)
                .eq(AgentThread::getSessionId, gate.getSessionId()));
        Set<Long> artifactIds = collectGateArtifactIds(gate.getGateCode(), threads);
        if (artifactIds.isEmpty()) {
            warnings.add("no output artifacts found for gate " + gate.getGateCode());
        }
        for (Long artifactId : artifactIds) {
            appendArtifactSnapshot(artifactId, artifacts, warnings);
        }

        quality.put("status", warnings.isEmpty() ? "passed" : "warning");
        quality.put("artifactCount", artifacts.size());
        quality.set("warnings", warnings);
        quality.set("artifacts", artifacts);
        return quality;
    }

    private Set<Long> collectGateArtifactIds(String gateCode, List<AgentThread> threads) {
        Set<String> targetStages = targetStagesForGate(gateCode);
        Set<Long> allOutputArtifactIds = new LinkedHashSet<>();
        Set<Long> gateOutputArtifactIds = new LinkedHashSet<>();
        for (AgentThread thread : threads) {
            if (thread.getOutputArtifactId() == null) {
                continue;
            }
            allOutputArtifactIds.add(thread.getOutputArtifactId());
            String stageCode = readStageCode(thread.getContextJson());
            if (targetStages.isEmpty() || targetStages.contains(stageCode)) {
                gateOutputArtifactIds.add(thread.getOutputArtifactId());
            }
        }
        return gateOutputArtifactIds.isEmpty() && targetStages.isEmpty() ? allOutputArtifactIds : gateOutputArtifactIds;
    }

    private Set<String> targetStagesForGate(String gateCode) {
        if ("requirement_confirmed".equals(gateCode)) {
            return Set.of("requirement_analysis");
        }
        if ("design_confirmed".equals(gateCode)) {
            return Set.of("technical_design");
        }
        if ("implementation_done".equals(gateCode)) {
            return Set.of("backend_implementation", "frontend_implementation");
        }
        if ("tests_passed".equals(gateCode)) {
            return Set.of("testing");
        }
        if ("delivery_confirmed".equals(gateCode)) {
            return Set.of("review_and_delivery");
        }
        return Set.of();
    }

    private String readStageCode(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return null;
        }
        try {
            JsonNode stageCode = objectMapper.readTree(contextJson).path("stageCode");
            return stageCode.isTextual() ? stageCode.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void appendArtifactSnapshot(Long artifactId, ArrayNode artifacts, ArrayNode warnings) {
        TaskArtifact artifact;
        try {
            artifact = taskArtifactService.getArtifact(artifactId);
        } catch (Exception ignored) {
            ObjectNode missing = objectMapper.createObjectNode();
            missing.put("artifactId", artifactId);
            missing.put("available", false);
            artifacts.add(missing);
            warnings.add("artifact #" + artifactId + " is unavailable");
            return;
        }

        ObjectNode item = objectMapper.createObjectNode();
        item.put("artifactId", artifact.getId());
        item.put("available", true);
        putText(item, "artifactType", artifact.getArtifactType());
        putText(item, "artifactName", artifact.getArtifactName());
        boolean hasContent = artifact.getContentText() != null && !artifact.getContentText().isBlank();
        boolean hasStorageUrl = artifact.getStorageUrl() != null && !artifact.getStorageUrl().isBlank();
        item.put("hasContent", hasContent);
        item.put("hasStorageUrl", hasStorageUrl);
        if (!hasContent && !hasStorageUrl) {
            warnings.add("artifact #" + artifact.getId() + " has no content or storage url");
        }
        appendMetadataSnapshot(artifact, item, warnings);
        artifacts.add(item);
    }

    private void appendMetadataSnapshot(TaskArtifact artifact, ObjectNode item, ArrayNode warnings) {
        JsonNode metadata = null;
        if (artifact.getMetadataJson() != null && !artifact.getMetadataJson().isBlank()) {
            try {
                metadata = objectMapper.readTree(artifact.getMetadataJson());
            } catch (Exception ignored) {
                warnings.add("artifact #" + artifact.getId() + " metadata json is invalid");
            }
        }
        requireMetadataNumber(artifact, metadata, item, warnings, "artifactVersion");
        requireMetadataNumber(artifact, metadata, item, warnings, "producerAgentId");
        requireMetadataNumber(artifact, metadata, item, warnings, "producerAgentVersionId");
        requireMetadataNumber(artifact, metadata, item, warnings, "collaborationSessionId");
        requireMetadataText(artifact, metadata, item, warnings, "stageCode");
    }

    private void requireMetadataNumber(TaskArtifact artifact,
                                       JsonNode metadata,
                                       ObjectNode item,
                                       ArrayNode warnings,
                                       String fieldName) {
        JsonNode value = metadata == null ? null : metadata.path(fieldName);
        if (value != null && value.isIntegralNumber()) {
            item.put(fieldName, value.asLong());
            return;
        }
        warnings.add("artifact #" + artifact.getId() + " missing metadata " + fieldName);
    }

    private void requireMetadataText(TaskArtifact artifact,
                                     JsonNode metadata,
                                     ObjectNode item,
                                     ArrayNode warnings,
                                     String fieldName) {
        JsonNode value = metadata == null ? null : metadata.path(fieldName);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            item.put(fieldName, value.asText());
            return;
        }
        warnings.add("artifact #" + artifact.getId() + " missing metadata " + fieldName);
    }

    private void putText(ObjectNode item, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            item.put(fieldName, value);
        }
    }
}
