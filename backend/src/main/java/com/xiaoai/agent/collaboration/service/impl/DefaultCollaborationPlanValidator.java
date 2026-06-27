package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.entity.ArtifactType;
import com.xiaoai.agent.collaboration.mapper.AgentRoleMapper;
import com.xiaoai.agent.collaboration.mapper.ArtifactTypeMapper;
import com.xiaoai.agent.collaboration.model.CollaborationPlanValidationResult;
import com.xiaoai.agent.collaboration.service.CollaborationPlanValidator;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.mapper.ToolConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultCollaborationPlanValidator implements CollaborationPlanValidator {

    private final AgentRoleMapper agentRoleMapper;
    private final ArtifactTypeMapper artifactTypeMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final ToolConfigMapper toolConfigMapper;
    private final int maxThreadsLimit;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultCollaborationPlanValidator(AgentRoleMapper agentRoleMapper,
                                             ArtifactTypeMapper artifactTypeMapper,
                                             @Value("${xiaoai.collaboration.plan.max-threads:6}") int maxThreadsLimit) {
        this(agentRoleMapper, artifactTypeMapper, null, null, maxThreadsLimit);
    }

    @Autowired
    public DefaultCollaborationPlanValidator(AgentRoleMapper agentRoleMapper,
                                             ArtifactTypeMapper artifactTypeMapper,
                                             AgentVersionMapper agentVersionMapper,
                                             ToolConfigMapper toolConfigMapper,
                                             @Value("${xiaoai.collaboration.plan.max-threads:6}") int maxThreadsLimit) {
        this.agentRoleMapper = agentRoleMapper;
        this.artifactTypeMapper = artifactTypeMapper;
        this.agentVersionMapper = agentVersionMapper;
        this.toolConfigMapper = toolConfigMapper;
        this.maxThreadsLimit = maxThreadsLimit;
    }

    @Override
public CollaborationPlanValidationResult validate(String planJson) {
        Long tenantId = UserContextHolder.requireTenantId();
        List<String> errors = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(planJson);
        } catch (Exception exception) {
            return CollaborationPlanValidationResult.failed(List.of("planJson is invalid JSON"));
        }
        if (root.path("goal").asText("").isBlank()) {
            errors.add("goal is required");
        }
        int maxDepth = root.path("maxDepth").asInt(1);
        if (maxDepth > 1) {
            errors.add("maxDepth exceeds MVP limit 1");
        }
        int maxThreads = root.path("maxThreads").asInt(1);
        if (maxThreads > maxThreadsLimit) {
            errors.add("maxThreads exceeds limit " + maxThreadsLimit);
        }
        validateHandoffPolicy(root.path("handoffPolicy"), errors);
        JsonNode stages = root.path("stages");
        if (!stages.isArray() || stages.isEmpty()) {
            errors.add("stages is required");
        } else {
            validateStages(tenantId, stages, errors);
            validateReworkStages(stages, errors);
        }
        return errors.isEmpty()
                ? CollaborationPlanValidationResult.passed()
                : CollaborationPlanValidationResult.failed(errors);
    }

    private void validateStages(Long tenantId, JsonNode stages, List<String> errors) {
        for (JsonNode stage : stages) {
            validateStageHandoffPolicy(stage, errors);
            if (stage.path("autoStartTask").asBoolean(false) && !stage.path("createTask").asBoolean(false)) {
                errors.add("autoStartTask requires createTask");
            }
            if (stage.path("createTask").asBoolean(false) && !stage.hasNonNull("agentId")) {
                errors.add("createTask requires agentId");
            }
            if (stage.path("autoStartTask").asBoolean(false) && !stage.hasNonNull("agentId")) {
                errors.add("autoStartTask requires agentId");
            }
            if (hasStageToolCalls(stage) && !stage.path("createTask").asBoolean(false)) {
                errors.add("stage toolCalls requires createTask");
            }
            if (hasStageToolCalls(stage) && !stage.hasNonNull("agentVersionId")) {
                errors.add("stage toolCalls requires agentVersionId");
            }
            String roleCode = stage.path("roleCode").asText("");
            if (!roleCode.isBlank() && !roleExists(tenantId, roleCode)) {
                errors.add("Role not found: " + roleCode);
            }
            validateToolCalls(stage.path("toolCalls"), errors);
            validateRegisteredToolCalls(tenantId, stage.path("toolCalls"), errors);
            validateAgentVersionToolScope(tenantId, stage, errors);
            validatePositiveLongField(stage, "inputArtifactVersion", errors);
            validateArtifactTypes(tenantId, stage.path("inputArtifactTypes"), errors);
            validateArtifactTypes(tenantId, stage.path("outputArtifactTypes"), errors);
        }
    }

    private void validateHandoffPolicy(JsonNode handoffPolicy, List<String> errors) {
        if (handoffPolicy.isMissingNode() || handoffPolicy.isNull()) {
            return;
        }
        if (!handoffPolicy.isObject()) {
            errors.add("handoffPolicy must be an object");
            return;
        }
        String mode = handoffPolicy.path("mode").asText("");
        if (!mode.isBlank() && !Set.of("strict", "lenient").contains(mode)) {
            errors.add("handoffPolicy.mode is invalid");
        }
        JsonNode requireAccepted = handoffPolicy.path("requireAcceptedBeforeConsume");
        if (!requireAccepted.isMissingNode() && !requireAccepted.isBoolean()) {
            errors.add("handoffPolicy.requireAcceptedBeforeConsume must be boolean");
        }
    }

    private void validateStageHandoffPolicy(JsonNode stage, List<String> errors) {
        validateBooleanField(stage, "waitForHandoffAcceptance", errors);
        validateBooleanField(stage, "requireAcceptedInputHandoff", errors);
        validateBooleanField(stage, "requireAcceptedHandoff", errors);
    }

    private void validateBooleanField(JsonNode node, String fieldName, List<String> errors) {
        JsonNode value = node.path(fieldName);
        if (!value.isMissingNode() && !value.isBoolean()) {
            errors.add(fieldName + " must be boolean");
        }
    }

    private void validatePositiveLongField(JsonNode node, String fieldName, List<String> errors) {
        JsonNode value = node.path(fieldName);
        if (!value.isMissingNode() && (!value.isIntegralNumber() || value.asLong() <= 0)) {
            errors.add(fieldName + " must be a positive integer");
        }
    }

    private void validateReworkStages(JsonNode stages, List<String> errors) {
        Set<String> stageCodes = new HashSet<>();
        for (JsonNode stage : stages) {
            String stageCode = stage.path("stageCode").asText("");
            if (!stageCode.isBlank()) {
                stageCodes.add(stageCode);
            }
        }
        for (JsonNode stage : stages) {
            String reworkStageCode = stage.path("reworkStageCode").asText("");
            if (!reworkStageCode.isBlank() && !stageCodes.contains(reworkStageCode)) {
                errors.add("reworkStageCode not found: " + reworkStageCode);
            }
        }
    }

    private void validateToolCalls(JsonNode toolCalls, List<String> errors) {
        if (toolCalls.isMissingNode() || toolCalls.isNull()) {
            return;
        }
        if (!toolCalls.isArray()) {
            errors.add("stage toolCalls must be an array");
            return;
        }
        if (toolCalls.isEmpty()) {
            errors.add("stage toolCalls must not be empty");
            return;
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            JsonNode toolCall = toolCalls.get(index);
            if (!toolCall.isObject()) {
                errors.add("stage toolCalls[" + index + "] must be an object");
                continue;
            }
            if (!toolCall.hasNonNull("toolId")) {
                errors.add("stage toolCalls[" + index + "] missing toolId");
            }
            JsonNode payload = toolCall.path("callPayloadJson");
            if (!payload.isMissingNode() && !payload.isObject()) {
                errors.add("stage toolCalls[" + index + "] callPayloadJson must be an object");
            }
        }
    }

    private boolean hasStageToolCalls(JsonNode stage) {
        JsonNode toolCalls = stage.path("toolCalls");
        return toolCalls.isArray() && !toolCalls.isEmpty();
    }

    private void validateRegisteredToolCalls(Long tenantId, JsonNode toolCalls, List<String> errors) {
        if (toolConfigMapper == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
            return;
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            JsonNode toolCall = toolCalls.get(index);
            if (!toolCall.isObject() || !toolCall.hasNonNull("toolId")) {
                continue;
            }
            Long toolId = toolCall.path("toolId").asLong();
            ToolConfig tool = findTool(tenantId, toolId);
            if (tool == null) {
                errors.add("Tool not found: " + toolId);
                continue;
            }
            String declaredToolCode = toolCall.path("toolCode").asText("");
            if (!declaredToolCode.isBlank() && !declaredToolCode.equals(tool.getToolCode())) {
                errors.add("stage toolCalls[" + index + "] toolCode mismatch: " + declaredToolCode);
            }
            if (!"active".equals(tool.getStatus())) {
                errors.add("stage toolCalls[" + index + "] tool is not active: " + tool.getToolCode());
            }
        }
    }

    private void validateAgentVersionToolScope(Long tenantId, JsonNode stage, List<String> errors) {
        JsonNode toolCalls = stage.path("toolCalls");
        if (!toolCalls.isArray() || toolCalls.isEmpty() || !stage.hasNonNull("agentVersionId")) {
            return;
        }
        if (agentVersionMapper == null || toolConfigMapper == null) {
            return;
        }
        Long agentVersionId = stage.path("agentVersionId").asLong();
        AgentVersion version = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getTenantId, tenantId)
                .eq(AgentVersion::getId, agentVersionId)
                .last("limit 1"));
        if (version == null) {
            errors.add("Agent version not found: " + agentVersionId);
            return;
        }
        if (stage.hasNonNull("agentId") && version.getAgentId() != null
                && !version.getAgentId().equals(stage.path("agentId").asLong())) {
            errors.add("Agent version does not belong to stage agent: " + agentVersionId);
            return;
        }
        ToolScope scope = parseToolScope(version.getToolScopeJson());
        if (scope.isEmpty()) {
            return;
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            JsonNode toolCall = toolCalls.get(index);
            if (!toolCall.isObject() || !toolCall.hasNonNull("toolId")) {
                continue;
            }
            Long toolId = toolCall.path("toolId").asLong();
            ToolConfig tool = findTool(tenantId, toolId);
            if (tool == null) {
                continue;
            }
            if (!scope.toolIds().contains(tool.getId()) && !scope.toolCodes().contains(tool.getToolCode())) {
                errors.add("stage toolCalls[" + index + "] tool not in agent version scope: " + tool.getToolCode());
            }
        }
    }

    private ToolConfig findTool(Long tenantId, Long toolId) {
        return toolConfigMapper.selectOne(new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getTenantId, tenantId)
                .eq(ToolConfig::getId, toolId)
                .last("limit 1"));
    }

    private ToolScope parseToolScope(String toolScopeJson) {
        if (toolScopeJson == null || toolScopeJson.isBlank()) {
            return ToolScope.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(toolScopeJson);
            if (!root.isArray()) {
                return ToolScope.empty();
            }
            Set<Long> toolIds = new HashSet<>();
            Set<String> toolCodes = new HashSet<>();
            for (JsonNode item : root) {
                JsonNode toolId = item.get("toolId");
                if (toolId != null && toolId.canConvertToLong()) {
                    toolIds.add(toolId.asLong());
                }
                JsonNode toolCode = item.get("toolCode");
                if (toolCode != null && toolCode.isTextual() && !toolCode.asText().isBlank()) {
                    toolCodes.add(toolCode.asText());
                }
            }
            return new ToolScope(toolIds, toolCodes);
        } catch (Exception ignored) {
            return ToolScope.empty();
        }
    }

    private void validateArtifactTypes(Long tenantId, JsonNode typeCodes, List<String> errors) {
        if (!typeCodes.isArray()) {
            return;
        }
        for (JsonNode typeCodeNode : typeCodes) {
            String typeCode = typeCodeNode.asText("");
            if (!typeCode.isBlank() && !artifactTypeExists(tenantId, typeCode)) {
                errors.add("Artifact type not found: " + typeCode);
            }
        }
    }

    private boolean roleExists(Long tenantId, String roleCode) {
        return agentRoleMapper.selectOne(new LambdaQueryWrapper<AgentRole>()
                .eq(AgentRole::getTenantId, tenantId)
                .eq(AgentRole::getRoleCode, roleCode)
                .last("limit 1")) != null;
    }

    private boolean artifactTypeExists(Long tenantId, String typeCode) {
        return artifactTypeMapper.selectOne(new LambdaQueryWrapper<ArtifactType>()
                .eq(ArtifactType::getTenantId, tenantId)
                .eq(ArtifactType::getTypeCode, typeCode)
                .last("limit 1")) != null;
    }

    private record ToolScope(Set<Long> toolIds, Set<String> toolCodes) {

        private static ToolScope empty() {
            return new ToolScope(Set.of(), Set.of());
        }

        private boolean isEmpty() {
            return toolIds.isEmpty() && toolCodes.isEmpty();
        }
    }
}
