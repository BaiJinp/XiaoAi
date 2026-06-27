package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ContextPackageBuilder {

    private final ObjectMapper objectMapper;

    public ContextPackageBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
public ContextPackage build(RunStartCommand command) {
        JsonNode inputRoot = parseObject(command.getInputText());
        JsonNode runtimeSnapshotRoot = parseObject(command.getRuntimeSnapshotJson());
        return ContextPackage.from(command, resolvePromptInputText(command.getInputText(), inputRoot), inputRoot, runtimeSnapshotRoot, resolveExecutionMode(inputRoot, runtimeSnapshotRoot));
    }

    private String resolvePromptInputText(String rawInputText, JsonNode inputRoot) {
        String wrappedInputText = text(inputRoot, "inputText");
        return StringUtils.hasText(wrappedInputText) ? wrappedInputText : rawInputText;
    }

    private ExecutionMode resolveExecutionMode(JsonNode inputRoot, JsonNode runtimeSnapshotRoot) {
        String explicitMode = text(inputRoot, "executionMode");
        if (StringUtils.hasText(explicitMode)) {
            return ExecutionMode.fromCode(explicitMode);
        }

        String snapshotMode = nestedText(runtimeSnapshotRoot, "orchestrationPolicy", "executionMode");
        if (!StringUtils.hasText(snapshotMode)) {
            snapshotMode = text(runtimeSnapshotRoot, "executionMode");
        }
        if (StringUtils.hasText(snapshotMode)) {
            return ExecutionMode.fromCode(snapshotMode);
        }

        if (has(inputRoot, "collaborationSessionId") || has(inputRoot, "strategyType")) {
            return ExecutionMode.MULTI_AGENT;
        }
        if (has(inputRoot, "workflowSteps")) {
            return ExecutionMode.DYNAMIC_WORKFLOW;
        }
        if (has(inputRoot, "toolId") && !has(inputRoot, "assistantTaskType")
                && !has(inputRoot, "modelId") && !has(inputRoot, "knowledgeBaseId")) {
            return ExecutionMode.DIRECT_TOOL;
        }
        return ExecutionMode.SINGLE_AGENT;
    }

    private JsonNode parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean has(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName);
    }

    private String text(JsonNode node, String fieldName) {
        return has(node, fieldName) ? node.get(fieldName).asText() : null;
    }

    private String nestedText(JsonNode node, String objectFieldName, String fieldName) {
        if (node == null || !node.hasNonNull(objectFieldName) || !node.get(objectFieldName).isObject()) {
            return null;
        }
        return text(node.get(objectFieldName), fieldName);
    }
}
