package com.xiaoai.agent.runtime.gateway;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.xiaoai.agent.runtime.engine.AgentLoopExecutor;
import com.xiaoai.agent.runtime.engine.AgentLoopPhase;
import com.xiaoai.agent.runtime.engine.AgentRunEngine;
import com.xiaoai.agent.runtime.engine.ContextPackage;
import com.xiaoai.agent.runtime.engine.DefaultAgentLoopExecutor;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;
import com.xiaoai.agent.runtime.model.RunApprovalResultCommand;
import com.xiaoai.agent.runtime.model.RunCancelCommand;
import com.xiaoai.agent.runtime.model.RunResumeCommand;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import com.xiaoai.agent.runtime.model.RunUserInputCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import com.xiaoai.agent.safety.PromptSafetyValidator;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JavaInProcessRuntimeGateway implements RuntimeGateway {

    private static final Logger log = LoggerFactory.getLogger(JavaInProcessRuntimeGateway.class);

    private final ToolConfigService toolConfigService;
    private final AgentVersionService agentVersionService;
    private final ModelGateway modelGateway;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final AgentMemoryService agentMemoryService;
    private final RuntimeCheckpointService runtimeCheckpointService;
    private final AgentRunEngine agentRunEngine;
    private final AgentLoopExecutor agentLoopExecutor;
    private final PromptSafetyValidator promptSafetyValidator;
    private final ObjectMapper objectMapper;
    private final Map<Long, List<RuntimeEvent>> eventCache = new ConcurrentHashMap<>();
    private final Map<Long, SuspendedToolCall> suspendedToolCalls = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> approvedApprovalRequests = new ConcurrentHashMap<>();

    @Autowired
    public JavaInProcessRuntimeGateway(ToolConfigService toolConfigService,
                                       AgentVersionService agentVersionService,
                                       ModelGateway modelGateway,
                                       KnowledgeDocumentService knowledgeDocumentService,
                                       AgentMemoryService agentMemoryService,
                                       RuntimeCheckpointService runtimeCheckpointService,
                                       AgentRunEngine agentRunEngine,
                                       PromptSafetyValidator promptSafetyValidator,
                                       ObjectMapper objectMapper) {
        this.toolConfigService = toolConfigService;
        this.agentVersionService = agentVersionService;
        this.modelGateway = modelGateway;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.agentMemoryService = agentMemoryService;
        this.runtimeCheckpointService = runtimeCheckpointService;
        this.agentRunEngine = agentRunEngine;
        this.promptSafetyValidator = promptSafetyValidator;
        this.agentLoopExecutor = new DefaultAgentLoopExecutor(
                modelGateway, toolConfigService, agentVersionService,
                knowledgeDocumentService, agentMemoryService, objectMapper, eventCache);
        this.objectMapper = objectMapper;
    }

    public JavaInProcessRuntimeGateway(ToolConfigService toolConfigService,
                                       AgentVersionService agentVersionService,
                                       ModelGateway modelGateway,
                                       KnowledgeDocumentService knowledgeDocumentService,
                                       RuntimeCheckpointService runtimeCheckpointService,
                                       AgentRunEngine agentRunEngine,
                                       ObjectMapper objectMapper) {
        this(toolConfigService, agentVersionService, modelGateway, knowledgeDocumentService, null,
                runtimeCheckpointService, agentRunEngine, objectMapper);
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
        // Prompt 注入检测
        String userInput = extractUserInput(command);
        if (StringUtils.hasText(userInput) && promptSafetyValidator != null) {
            PromptSafetyValidator.ValidationResult validation = promptSafetyValidator.validate(userInput);
            if (validation.isInjection()) {
                log.warn("Detected prompt injection attempt: userId={}, patterns={}",
                        command.getUserId(), validation.getDetectedPatterns());

                // 记录注入尝试事件
                recordInjectionAttempt(command, validation);

                // 返回失败结果
                return RunStartResult.builder()
                        .success(false)
                        .errorMessage("检测到潜在的安全风险输入，已拒绝执行。" + validation.getWarningMessage())
                        .build();
            }
        }

        return agentRunEngine.start(command, contextPackage -> {
            // 检查是否使用 Agent Loop 模式
            if (contextPackage.getExecutionMode() == ExecutionMode.AGENT_LOOP) {
                executeAgentLoop(command, contextPackage);
            } else {
                executeCurrentRuntimePlan(command, contextPackage);
            }
        });
    }

    /**
     * 提取用户输入
     */
    private String extractUserInput(RunStartCommand command) {
        if (!StringUtils.hasText(command.getInputText())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(command.getInputText());
            if (root.hasNonNull("inputText")) {
                return root.get("inputText").asText();
            }
            return command.getInputText();
        } catch (Exception e) {
            return command.getInputText();
        }
    }

    /**
     * 记录注入尝试
     */
    private void recordInjectionAttempt(RunStartCommand command, PromptSafetyValidator.ValidationResult validation) {
        record(command, "PROMPT_INJECTION_DETECTED", "Prompt injection attempt detected",
                "{\"userId\":" + command.getUserId()
                        + ",\"patterns\":" + toJsonArray(validation.getDetectedPatterns())
                        + ",\"warning\":\"" + safeJson(validation.getWarningMessage()) + "\"}");
    }

    /**
     * 将字符串列表转为 JSON 数组
     */
    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(safeJson(items.get(i))).append("\"");
        }
        json.append("]");
        return json.toString();
    }

    private void executeAgentLoop(RunStartCommand command, ContextPackage contextPackage) {
        eventCache.put(command.getRunId(), new ArrayList<>());
        record(command, "AGENT_LOOP_START", "Starting agent loop execution", "{}");

        try {
            AgentLoopExecutor.AgentLoopResult result = agentLoopExecutor.execute(command, contextPackage);

            record(command, "AGENT_LOOP_COMPLETE", "Agent loop execution completed",
                    "{\"status\":\"" + result.getFinalStatus() + "\""
                            + ",\"loopCount\":" + result.getLoopCount()
                            + ",\"elapsedMs\":" + result.getElapsedMs()
                            + ",\"summary\":\"" + safeJson(result.getResultSummary()) + "\"}");

            // 如果成功，生成最终交付物
            if ("success".equals(result.getFinalStatus())) {
                recordAgentLoopArtifact(command, result);
            }

        } catch (Exception e) {
            record(command, "AGENT_LOOP_ERROR", "Agent loop execution failed",
                    "{\"error\":\"" + safeJson(e.getMessage()) + "\"}");
        }
    }

    private void recordAgentLoopArtifact(RunStartCommand command, AgentLoopExecutor.AgentLoopResult result) {
        String artifactContent = buildAgentLoopArtifactContent(result);
        record(command, "ASSISTANT_ARTIFACT", "Agent loop artifact generated",
                "{\"artifactType\":\"agent_loop_result\""
                        + ",\"artifactName\":\"Agent Loop Execution Result\""
                        + ",\"contentText\":\"" + safeJson(artifactContent) + "\"}");
    }

    private String buildAgentLoopArtifactContent(AgentLoopExecutor.AgentLoopResult result) {
        return "# Agent Loop Execution Result\n\n"
                + "**Status:** " + result.getFinalStatus() + "\n\n"
                + "**Loop Count:** " + result.getLoopCount() + "\n\n"
                + "**Elapsed Time:** " + result.getElapsedMs() + "ms\n\n"
                + "**Summary:**\n\n" + (result.getResultSummary() != null ? result.getResultSummary() : "N/A") + "\n";
    }


    private void executeCurrentRuntimePlan(RunStartCommand command, ContextPackage contextPackage) {
        eventCache.put(command.getRunId(), new ArrayList<>());
        JsonNode root = contextPackage.getInputRoot();
        List<AgentMemory> memories = loadRuntimeMemories(command, root, contextPackage.getRuntimeSnapshotRoot());
        recordMemoryContextIfPresent(command, memories);
        if (!executeProjectAssistantIfRequested(command, root, memories)) {
            retrieveKnowledgeIfRequested(command, root);
            executeModelIfRequested(command, root, memories);
            executeToolIfRequested(command, root);
        }
    }

    private boolean executeProjectAssistantIfRequested(RunStartCommand command, JsonNode root, List<AgentMemory> memories) {
        if (root == null || !root.hasNonNull("assistantTaskType")) {
            return false;
        }
        String assistantTaskType = root.get("assistantTaskType").asText();
        long stepId = 1L;
        recordAgentLoopObserved(command, assistantTaskType, root, memories);
        recordStepStarted(command, stepId, "plan", "生成执行计划", assistantTaskType);
        record(command, stepId, "ASSISTANT_PLAN_CREATED", "Project assistant plan created",
                "{\"assistantTaskType\":\"" + safeJson(assistantTaskType)
                        + "\",\"workflowSteps\":" + buildProjectAssistantWorkflowSteps(root, assistantTaskType) + "}");
        recordStepCompleted(command, stepId, "plan", "生成执行计划", assistantTaskType, "success");

        List<KnowledgeRetrieveResult> knowledgeResults = List.of();
        if (root.hasNonNull("knowledgeBaseId")) {
            stepId++;
            recordStepStarted(command, stepId, "knowledge", "检索项目资料", assistantTaskType);
            knowledgeResults = retrieveKnowledge(command, root, defaultQuery(root, assistantTaskType), stepId);
            recordStepCompleted(command, stepId, "knowledge", "检索项目资料", assistantTaskType,
                    isLowConfidence(knowledgeResults) ? "warning" : "success");
        }

        if (root.hasNonNull("modelId")) {
            stepId++;
            recordStepStarted(command, stepId, "model_call", "生成分析内容", assistantTaskType);
            String prompt = buildAssistantPrompt(root, assistantTaskType, knowledgeResults, memories);
            executeModel(command, stepId, root.get("modelId").asLong(), prompt);
            recordStepCompleted(command, stepId, "model_call", "生成分析内容", assistantTaskType, "success");
        }

        if (hasToolExecution(root)) {
            stepId++;
            recordStepStarted(command, stepId, "tool_call", "执行受控工具", assistantTaskType);
            String toolStatus = executeToolPlan(command, root, false, stepId, null);
            if (!"success".equals(toolStatus)) {
                recordStepCompleted(command, stepId, "tool_call", "执行受控工具", assistantTaskType, toolStatus);
                record(command, stepId, "ASSISTANT_TASK_SUSPENDED", "Project assistant task suspended",
                        "{\"assistantTaskType\":\"" + safeJson(assistantTaskType)
                                + "\",\"reason\":\"tool_" + safeJson(toolStatus) + "\"}");
                return true;
            }
            recordStepCompleted(command, stepId, "tool_call", "执行受控工具", assistantTaskType, "success");
        }

        stepId++;
        recordStepStarted(command, stepId, "artifact", "生成结构化交付物", assistantTaskType);
        recordAssistantArtifact(command, stepId, assistantTaskType, root, knowledgeResults);
        recordStepCompleted(command, stepId, "artifact", "生成结构化交付物", assistantTaskType, "success");

        stepId++;
        recordStepStarted(command, stepId, "self_check", "规则自检", assistantTaskType);
        recordStepCompleted(command, stepId, "self_check", "规则自检", assistantTaskType,
                isLowConfidence(knowledgeResults) ? "warning" : "success");

        record(command, stepId, "ASSISTANT_TASK_COMPLETED", "Project assistant task completed",
                "{\"assistantTaskType\":\"" + safeJson(assistantTaskType) + "\"}");
        return true;
    }

    private void recordAssistantArtifact(RunStartCommand command,
                                         Long stepId,
                                         String assistantTaskType,
                                         JsonNode root,
                                         List<KnowledgeRetrieveResult> knowledgeResults) {
        ArtifactPayload payload = buildArtifactPayload(assistantTaskType, root, knowledgeResults);
        record(command, stepId, "ASSISTANT_ARTIFACT", "Project assistant artifact generated",
                "{\"artifactType\":\"" + safeJson(payload.artifactType())
                        + "\",\"artifactName\":\"" + safeJson(payload.artifactName())
                        + "\",\"contentText\":\"" + safeJson(payload.contentText())
                        + "\",\"metadata\":" + buildArtifactMetadata(assistantTaskType, knowledgeResults) + "}");
    }

    private ArtifactPayload buildArtifactPayload(String assistantTaskType,
                                                 JsonNode root,
                                                 List<KnowledgeRetrieveResult> knowledgeResults) {
        ArtifactPayload collaborationPayload = buildCollaborationArtifactPayload(assistantTaskType, root);
        if (collaborationPayload != null) {
            return collaborationPayload;
        }
        String userPrompt = root.hasNonNull("prompt") ? root.get("prompt").asText() : defaultPrompt(assistantTaskType);
        String evidence = summarizeEvidence(knowledgeResults, userPrompt);
        if ("meeting_minutes".equals(assistantTaskType)) {
            return new ArtifactPayload(
                    "meeting_action_items",
                    "会议行动项",
                    "[{\"title\":\"确认会议结论并拆解项目任务\",\"owner\":\"项目负责人\",\"dueDate\":\"待确认\",\"status\":\"待确认\","
                            + "\"basis\":\"" + safeJson(evidence) + "\"},"
                            + "{\"title\":\"跟进高风险动作审批后的任务创建结果\",\"owner\":\"项目助理 Agent\",\"dueDate\":\"审批通过后\",\"status\":\"待审批\"}]"
            );
        }
        if ("risk_analysis".equals(assistantTaskType)) {
            return new ArtifactPayload(
                    "risk_analysis",
                    "项目风险清单",
                    "[{\"risk\":\"项目进度存在延期或阻塞风险\",\"impact\":\"可能影响后续联调、验收或交付节奏\","
                            + "\"mitigation\":\"基于最新项目资料确认责任人、依赖项和下一个检查点\",\"level\":\"high\","
                            + "\"basis\":\"" + safeJson(evidence) + "\"},"
                            + "{\"risk\":\"资料来源不足导致判断不确定\",\"impact\":\"风险等级和建议动作需要人工确认\","
                            + "\"mitigation\":\"补充会议纪要、任务状态或延期记录后重新分析\",\"level\":\"medium\"}]"
            );
        }
        return new ArtifactPayload(
                "weekly_report",
                "项目周报",
                buildWeeklyReportMarkdown(userPrompt, evidence, knowledgeResults)
        );
    }

    private ArtifactPayload buildCollaborationArtifactPayload(String assistantTaskType, JsonNode root) {
        if (assistantTaskType == null || !assistantTaskType.startsWith("agile_") || root == null) {
            return null;
        }
        String artifactType = firstText(root.path("outputArtifactTypes"));
        String stageCode = assistantTaskType.substring("agile_".length());
        if (!StringUtils.hasText(artifactType)) {
            artifactType = stageCode + "_artifact";
        }
        String prompt = root.hasNonNull("prompt") ? root.get("prompt").asText() : defaultPrompt(assistantTaskType);
        String artifactName = artifactType.replace('_', ' ');
        return new ArtifactPayload(
                artifactType,
                artifactName,
                "# " + artifactName + "\n\n"
                        + "- Stage: " + stageCode + "\n"
                        + "- Task: " + prompt + "\n"
                        + "- Status: completed\n"
        );
    }

    private String firstText(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        for (JsonNode item : node) {
            String value = item.asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String buildWeeklyReportMarkdown(String userPrompt,
                                             String evidence,
                                             List<KnowledgeRetrieveResult> knowledgeResults) {
        String confidence = knowledgeConfidence(knowledgeResults);
        String sourceLine = knowledgeResults.isEmpty()
                ? "当前未检索到项目资料，以下内容基于用户输入生成，建议补充资料后复核。"
                : "已参考 " + knowledgeResults.size() + " 条项目资料片段。";
        String confidenceLine = isLowConfidence(knowledgeResults)
                ? "- 当前资料可信度为 " + confidence + "，以下结论只作为初稿，需要项目负责人复核。\n"
                : "- 当前资料可信度为 " + confidence + "，可作为周报初稿依据。\n";
        return "# 项目周报\n\n"
                + "## 本周进展\n\n"
                + "- 已围绕用户目标进行分析：" + userPrompt + "\n"
                + "- " + sourceLine + "\n\n"
                + "## 依据可信度\n\n"
                + confidenceLine + "\n"
                + "## 关键成果\n\n"
                + "- 已整理项目当前进展、风险和下周建议动作。\n\n"
                + "## 风险与阻塞\n\n"
                + "- " + evidence + "\n"
                + "- 对资料不足或判断不确定的内容，需要项目负责人进一步确认。\n\n"
                + "## 下周计划\n\n"
                + "- 跟进高优先级风险项。\n"
                + "- 明确责任人、截止时间和依赖方。\n"
                + "- 更新项目资料后再次生成周报。\n\n"
                + "## 需要协调\n\n"
                + "- 请确认是否需要创建或更新项目任务。\n";
    }

    private String summarizeEvidence(List<KnowledgeRetrieveResult> knowledgeResults, String fallback) {
        if (knowledgeResults.isEmpty()) {
            return "未检索到可用项目资料，依据用户输入：" + fallback;
        }
        return knowledgeResults.stream()
                .map(KnowledgeRetrieveResult::getChunkText)
                .filter(StringUtils::hasText)
                .map(text -> text.length() > 80 ? text.substring(0, 80) + "..." : text)
                .findFirst()
                .orElse("已检索到项目资料，但片段内容为空");
    }

    private String buildArtifactMetadata(String assistantTaskType, List<KnowledgeRetrieveResult> knowledgeResults) {
        return "{\"assistantTaskType\":\"" + safeJson(assistantTaskType)
                + "\",\"knowledgeConfidence\":\"" + knowledgeConfidence(knowledgeResults) + "\""
                + ",\"lowConfidence\":" + isLowConfidence(knowledgeResults)
                + ",\"sources\":" + toSourcesJson(knowledgeResults)
                + ",\"sourceRefs\":" + toSourcesJson(knowledgeResults) + "}";
    }

    private void retrieveKnowledgeIfRequested(RunStartCommand command, JsonNode root) {
        if (root == null || !root.hasNonNull("knowledgeBaseId") || !root.hasNonNull("query")) {
            return;
        }
        retrieveKnowledge(command, root, root.get("query").asText(), null);
    }

    private List<KnowledgeRetrieveResult> retrieveKnowledge(RunStartCommand command, JsonNode root, String query, Long stepId) {
        Long knowledgeBaseId = root.get("knowledgeBaseId").asLong();
        Integer topK = root.hasNonNull("topK") ? root.get("topK").asInt() : 5;
        record(command, stepId, "KNOWLEDGE_RETRIEVE", "Knowledge retrieval requested",
                "{\"knowledgeBaseId\":" + knowledgeBaseId
                        + ",\"query\":\"" + safeJson(query) + "\",\"topK\":" + topK + "}");

        RetrieveKnowledgeCommand retrieveCommand = new RetrieveKnowledgeCommand();
        retrieveCommand.setKnowledgeBaseId(knowledgeBaseId);
        retrieveCommand.setQuery(query);
        retrieveCommand.setTopK(topK);
        List<KnowledgeRetrieveResult> results = knowledgeDocumentService.retrieve(retrieveCommand);
        record(command, stepId, "KNOWLEDGE_RESULT", "Knowledge retrieval completed",
                "{\"knowledgeBaseId\":" + knowledgeBaseId
                        + ",\"hitCount\":" + results.size()
                        + ",\"knowledgeConfidence\":\"" + knowledgeConfidence(results) + "\""
                        + ",\"lowConfidence\":" + isLowConfidence(results)
                        + ",\"sources\":" + toSourcesJson(results) + "}");
        return results;
    }

    private void executeModelIfRequested(RunStartCommand command, JsonNode root, List<AgentMemory> memories) {
        if (root == null || !root.hasNonNull("modelId") || !root.hasNonNull("prompt")) {
            return;
        }
        executeModel(command, null, root.get("modelId").asLong(), appendMemoryContext(root.get("prompt").asText(), memories));
    }

    private void executeModel(RunStartCommand command, Long stepId, Long modelId, String prompt) {
        record(command, stepId, "MODEL_CALL", "Model call requested",
                "{\"modelId\":" + modelId + ",\"prompt\":\"" + safeJson(prompt) + "\"}");

        ChatModelCommand modelCommand = new ChatModelCommand();
        modelCommand.setModelId(modelId);
        modelCommand.setTaskId(command.getTaskId());
        modelCommand.setRunId(command.getRunId());
        modelCommand.setPrompt(prompt);
        ChatModelResponse response = modelGateway.chat(modelCommand);
        record(command, stepId, "MODEL_RESULT", "Model call succeeded",
                "{\"modelCallLogId\":" + response.getModelCallLogId()
                        + ",\"totalTokens\":" + response.getTotalTokens()
                        + ",\"content\":\"" + safeJson(response.getContent()) + "\"}");
    }

    @Override
public void cancelRun(RunCancelCommand command) {
        // MVP skeleton: actual cancellation will be implemented with task execution.
    }

    @Override
public void resumeRun(RunResumeCommand command) {
        SuspendedToolCall suspendedToolCall = suspendedToolCalls.computeIfAbsent(command.getRunId(),
                runId -> loadSuspendedToolCall(command));
        if (suspendedToolCall == null
                || !isApprovalApproved(command.getRunId(), suspendedToolCall.approvalRequestId())
                || !claimCheckpointIfNeeded(suspendedToolCall)) {
            return;
        }
        if (denyResumedToolIfOutOfScope(suspendedToolCall)) {
            return;
        }
        suspendedToolCalls.remove(command.getRunId());
        record(suspendedToolCall.startCommand(), "TOOL_CALL", "Tool call resumed after approval",
                "{\"toolId\":" + suspendedToolCall.toolId()
                        + toolCallIndexJson(suspendedToolCall.toolCallIndex())
                        + toolMetadataJson(suspendedToolCall.toolType(), suspendedToolCall.toolCode(), suspendedToolCall.riskLevel())
                        + ",\"payload\":" + suspendedToolCall.callPayloadJson()
                        + ",\"approvalRequestId\":" + suspendedToolCall.approvalRequestId() + "}");
        String toolStatus = suspendedToolCall.toolCallIndex() == null
                ? executeTool(suspendedToolCall.startCommand(), suspendedToolCall.root(), true, null,
                suspendedToolCall.approvalRequestId())
                : executeToolChain(suspendedToolCall.startCommand(), suspendedToolCall.root(), true, null,
                suspendedToolCall.approvalRequestId(), suspendedToolCall.toolCallIndex());
        if ("success".equals(toolStatus)) {
            completeCheckpoint(command.getRunId(), suspendedToolCall.approvalRequestId());
            if (suspendedToolCall.root().hasNonNull("assistantTaskType")) {
                String assistantTaskType = suspendedToolCall.root().get("assistantTaskType").asText();
                recordAssistantArtifact(suspendedToolCall.startCommand(), null, assistantTaskType, suspendedToolCall.root(), List.of());
                record(suspendedToolCall.startCommand(), "ASSISTANT_TASK_COMPLETED", "Project assistant task completed after approval",
                        "{\"approvalRequestId\":" + suspendedToolCall.approvalRequestId() + "}");
            }
        }
    }

    private boolean denyResumedToolIfOutOfScope(SuspendedToolCall suspendedToolCall) {
        if (isToolAllowedByAgentVersion(suspendedToolCall.startCommand(), suspendedToolCall.toolId(), suspendedToolCall.toolCode())) {
            return false;
        }
        suspendedToolCalls.remove(suspendedToolCall.startCommand().getRunId());
        record(suspendedToolCall.startCommand(), "TOOL_DENIED", "Tool call denied by agent version tool scope on resume",
                "{\"toolId\":" + suspendedToolCall.toolId()
                        + toolCallIndexJson(suspendedToolCall.toolCallIndex())
                        + toolMetadataJson(suspendedToolCall.toolType(), suspendedToolCall.toolCode(), suspendedToolCall.riskLevel())
                        + ",\"agentVersionId\":" + suspendedToolCall.startCommand().getAgentVersionId()
                        + ",\"approvalRequestId\":" + suspendedToolCall.approvalRequestId()
                        + ",\"reason\":\"tool_not_in_agent_version_scope_on_resume\"}");
        return true;
    }

    @Override
public void submitUserInput(RunUserInputCommand command) {
        // MVP skeleton: user input will be routed to suspended runs later.
    }

    @Override
public void submitApprovalResult(RunApprovalResultCommand command) {
        if ("approved".equals(command.getApprovalStatus()) && command.getApprovalRequestId() != null) {
            approvedApprovalRequests.computeIfAbsent(command.getRunId(), key -> ConcurrentHashMap.newKeySet())
                    .add(command.getApprovalRequestId());
            approveCheckpoint(command);
        }
    }

    @Override
public List<RuntimeEvent> listEvents(Long runId) {
        return List.copyOf(eventCache.getOrDefault(runId, List.of()));
    }

    private void executeToolIfRequested(RunStartCommand command, JsonNode root) {
        if (root == null || !hasToolExecution(root)) {
            return;
        }
        executeToolPlan(command, root, false, null, null);
    }

    private String executeTool(RunStartCommand command, JsonNode root) {
        return executeTool(command, root, false, null);
    }

    private String executeTool(RunStartCommand command, JsonNode root, boolean approvalBypassed) {
        return executeTool(command, root, approvalBypassed, null);
    }

    private String executeTool(RunStartCommand command, JsonNode root, boolean approvalBypassed, Long stepId) {
        return executeTool(command, root, approvalBypassed, stepId, null);
    }

    private String executeToolPlan(RunStartCommand command,
                                   JsonNode root,
                                   boolean approvalBypassed,
                                   Long stepId,
                                   Long approvalRequestId) {
        if (hasToolCalls(root)) {
            return executeToolChain(command, root, approvalBypassed, stepId, approvalRequestId, 0);
        }
        return executeTool(command, root, approvalBypassed, stepId, approvalRequestId);
    }

    private String executeToolChain(RunStartCommand command,
                                    JsonNode root,
                                    boolean approvalBypassed,
                                    Long stepId,
                                    Long approvalRequestId,
                                    int startIndex) {
        JsonNode toolCalls = root.path("toolCalls");
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            return "success";
        }
        int safeStartIndex = Math.max(0, startIndex);
        for (int index = safeStartIndex; index < toolCalls.size(); index++) {
            String status = executeTool(command, root, toolCalls.get(index),
                    approvalBypassed && index == safeStartIndex,
                    stepId,
                    index == safeStartIndex ? approvalRequestId : null,
                    index);
            if (!"success".equals(status)) {
                return status;
            }
        }
        return "success";
    }

    private String executeTool(RunStartCommand command,
                               JsonNode root,
                               boolean approvalBypassed,
                               Long stepId,
                               Long approvalRequestId) {
        return executeTool(command, root, root, approvalBypassed, stepId, approvalRequestId, null);
    }

    private String executeTool(RunStartCommand command,
                               JsonNode root,
                               JsonNode toolNode,
                               boolean approvalBypassed,
                               Long stepId,
                               Long approvalRequestId,
                               Integer toolCallIndex) {
        Long toolId = toolNode.get("toolId").asLong();
        String callPayloadJson = toolNode.has("callPayloadJson")
                ? toolNode.get("callPayloadJson").toString()
                : root.has("callPayloadJson") ? root.get("callPayloadJson").toString() : "{}";
        ToolMetadata rootToolMetadata = toolMetadata(root, toolNode);
        if (!approvalBypassed) {
            record(command, stepId, "TOOL_CALL", "Tool call requested",
                    "{\"toolId\":" + toolId
                            + toolCallIndexJson(toolCallIndex)
                            + toolMetadataJson(rootToolMetadata)
                            + ",\"payload\":" + callPayloadJson + "}");
        }
        String toolCode = rootToolMetadata.toolCode();
        if (!isToolAllowedByAgentVersion(command, toolId, toolCode)) {
            record(command, stepId, "TOOL_DENIED", "Tool call denied by agent version tool scope",
                    "{\"toolId\":" + toolId
                            + toolCallIndexJson(toolCallIndex)
                            + toolMetadataJson(rootToolMetadata)
                            + ",\"agentVersionId\":" + command.getAgentVersionId()
                            + ",\"reason\":\"tool_not_in_agent_version_scope\"}");
            return "denied";
        }

        ExecuteToolCallCommand toolCommand = new ExecuteToolCallCommand();
        toolCommand.setToolId(toolId);
        toolCommand.setTaskId(command.getTaskId());
        toolCommand.setRunId(command.getRunId());
        toolCommand.setApplicantUserId(command.getUserId());
        toolCommand.setAgentVersionId(command.getAgentVersionId());
        toolCommand.setFallbackApproverUserId(firstNonNull(readLong(toolNode, "fallbackApproverUserId"), readLong(root, "fallbackApproverUserId")));
        toolCommand.setCallPayloadJson(callPayloadJson);
        toolCommand.setDryRun(Boolean.TRUE.equals(readBoolean(toolNode, "dryRun")) || Boolean.TRUE.equals(readBoolean(root, "dryRun")));
        toolCommand.setApprovalBypassed(approvalBypassed);
        toolCommand.setApprovalRequestId(approvalRequestId);

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(toolCommand);
        if ("success".equals(response.getStatus())) {
            record(command, stepId, "TOOL_RESULT", "Tool call succeeded",
                    "{\"toolCallLogId\":" + response.getToolCallLogId()
                            + toolCallIndexJson(toolCallIndex)
                            + toolMetadataJson(response)
                            + ",\"result\":" + safeJsonObject(response.getResultJson()) + "}");
            return "success";
        } else if ("blocked".equals(response.getStatus())) {
            suspendedToolCalls.put(command.getRunId(), new SuspendedToolCall(
                    command,
                    root,
                    toolId,
                    callPayloadJson,
                    response.getApprovalRequestId(),
                    firstNonBlank(response.getToolType(), rootToolMetadata.toolType()),
                    firstNonBlank(response.getToolCode(), rootToolMetadata.toolCode()),
                    firstNonBlank(response.getRiskLevel(), rootToolMetadata.riskLevel()),
                    toolCallIndex,
                    false
            ));
            saveCheckpoint(command, root, toolId, callPayloadJson, response.getApprovalRequestId(),
                    response.getToolType(), response.getToolCode(), response.getRiskLevel(), toolCallIndex);
            record(command, stepId, "TOOL_BLOCKED", "Tool call blocked",
                    "{\"toolCallLogId\":" + response.getToolCallLogId()
                            + ",\"approvalRequestId\":" + response.getApprovalRequestId()
                            + toolCallIndexJson(toolCallIndex)
                            + toolMetadataJson(response)
                            + ",\"reason\":\"" + safeJson(response.getErrorMessage()) + "\"}");
            return "blocked";
        } else {
            record(command, stepId, "TOOL_FAILED", "Tool call failed",
                    "{\"toolCallLogId\":" + response.getToolCallLogId()
                            + toolCallIndexJson(toolCallIndex)
                            + toolMetadataJson(response)
                            + ",\"error\":\"" + safeJson(response.getErrorMessage()) + "\"}");
            return "failed";
        }
    }

    private String defaultQuery(JsonNode root, String assistantTaskType) {
        if (root.hasNonNull("query")) {
            return root.get("query").asText();
        }
        if ("weekly_report".equals(assistantTaskType)) {
            return "项目进展 风险 下周计划";
        }
        if ("risk_analysis".equals(assistantTaskType)) {
            return "项目风险 延期 阻塞";
        }
        if ("meeting_minutes".equals(assistantTaskType)) {
            return "会议纪要 行动项 负责人";
        }
        return assistantTaskType;
    }

    private String buildAssistantPrompt(JsonNode root,
                                        String assistantTaskType,
                                        List<KnowledgeRetrieveResult> knowledgeResults,
                                        List<AgentMemory> memories) {
        String instruction = root.hasNonNull("prompt") ? root.get("prompt").asText() : defaultPrompt(assistantTaskType);
        StringBuilder prompt = new StringBuilder(instruction);
        if (!knowledgeResults.isEmpty()) {
            prompt.append("\n\n参考资料:");
            for (KnowledgeRetrieveResult result : knowledgeResults) {
                prompt.append("\n- [documentId=")
                        .append(result.getDocumentId())
                        .append(", chunkIndex=")
                        .append(result.getChunkIndex())
                        .append("] ")
                        .append(result.getChunkText());
            }
        }
        return appendMemoryContext(prompt.toString(), memories);
    }

    private List<AgentMemory> loadRuntimeMemories(RunStartCommand command, JsonNode root, JsonNode runtimeSnapshotRoot) {
        if (agentMemoryService == null || !isMemoryContextEnabled(runtimeSnapshotRoot)) {
            return List.of();
        }
        Long sessionId = firstNonNull(
                firstNonNull(readNullableLong(root, "sessionId"), readNullableLong(root, "collaborationSessionId")),
                firstNonNull(readNestedNullableLong(root, "collaborationContext", "sessionId"),
                        readNestedNullableLong(root, "collaborationContext", "collaborationSessionId"))
        );
        return agentMemoryService.listConfirmedMemoriesForRuntime(
                command.getTenantId(),
                command.getAgentId(),
                command.getTaskId(),
                sessionId,
                command.getUserId(),
                runtimeMemoryScopes(runtimeSnapshotRoot),
                runtimeMemoryLimit(runtimeSnapshotRoot)
        );
    }

    private boolean isMemoryContextEnabled(JsonNode runtimeSnapshotRoot) {
        JsonNode memoryPolicy = runtimeSnapshotRoot == null ? null : runtimeSnapshotRoot.get("memoryPolicy");
        if (memoryPolicy == null || !memoryPolicy.isObject() || !memoryPolicy.hasNonNull("enabled")) {
            return true;
        }
        return memoryPolicy.get("enabled").asBoolean();
    }

    private int runtimeMemoryLimit(JsonNode runtimeSnapshotRoot) {
        JsonNode memoryPolicy = runtimeSnapshotRoot == null ? null : runtimeSnapshotRoot.get("memoryPolicy");
        if (memoryPolicy == null || !memoryPolicy.isObject() || !memoryPolicy.hasNonNull("maxItems")) {
            return 5;
        }
        return Math.max(1, Math.min(memoryPolicy.get("maxItems").asInt(5), 20));
    }

    private List<String> runtimeMemoryScopes(JsonNode runtimeSnapshotRoot) {
        JsonNode memoryPolicy = runtimeSnapshotRoot == null ? null : runtimeSnapshotRoot.get("memoryPolicy");
        JsonNode scopes = memoryPolicy == null || !memoryPolicy.isObject() ? null : memoryPolicy.get("scopes");
        if (scopes == null || !scopes.isArray()) {
            return List.of("task");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode scope : scopes) {
            if (scope.isTextual() && StringUtils.hasText(scope.asText())) {
                result.add(scope.asText());
            }
        }
        return result.isEmpty() ? List.of("task") : result;
    }

    private void recordMemoryContextIfPresent(RunStartCommand command, List<AgentMemory> memories) {
        if (memories.isEmpty()) {
            return;
        }
        record(command, "MEMORY_CONTEXT", "Confirmed memory context loaded",
                "{\"memoryCount\":" + memories.size()
                        + ",\"memories\":" + toMemoriesJson(memories) + "}");
    }

    private String appendMemoryContext(String prompt, List<AgentMemory> memories) {
        if (memories.isEmpty()) {
            return prompt;
        }
        StringBuilder withMemory = new StringBuilder(prompt);
        withMemory.append("\n\n已确认记忆:");
        for (AgentMemory memory : memories) {
            withMemory.append("\n- ")
                    .append(memory.getSummaryText());
        }
        return withMemory.toString();
    }

    private String defaultPrompt(String assistantTaskType) {
        if ("weekly_report".equals(assistantTaskType)) {
            return "请基于参考资料生成项目周报，包含本周进展、风险、下周计划和待协调事项。";
        }
        if ("risk_analysis".equals(assistantTaskType)) {
            return "请基于参考资料生成项目风险分析，包含风险描述、依据、影响和建议动作。";
        }
        if ("meeting_minutes".equals(assistantTaskType)) {
            return "请基于参考资料生成会议纪要，包含结论、行动项、责任人和时间。";
        }
        return "请基于参考资料完成项目助理任务。";
    }

    private JsonNode parseInput(String inputText) {
        if (!StringUtils.hasText(inputText)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(inputText);
            return node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long readLong(JsonNode root, String fieldName) {
        return root.hasNonNull(fieldName) ? root.get(fieldName).asLong() : null;
    }

    private Boolean readBoolean(JsonNode root, String fieldName) {
        return root.hasNonNull(fieldName) && root.get(fieldName).asBoolean();
    }

    private String readText(JsonNode root, String fieldName) {
        return root.hasNonNull(fieldName) ? root.get(fieldName).asText() : null;
    }

    private String safeJsonObject(String value) {
        return StringUtils.hasText(value) ? value : "{}";
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String toSourcesJson(List<KnowledgeRetrieveResult> results) {
        List<String> sources = results.stream()
                .map(result -> "{\"chunkId\":" + result.getChunkId()
                        + ",\"documentId\":" + result.getDocumentId()
                        + ",\"chunkIndex\":" + result.getChunkIndex()
                        + ",\"score\":" + result.getScore()
                        + ",\"title\":\"" + safeJson(result.getSourceTitle()) + "\""
                        + ",\"sourceType\":\"" + safeJson(result.getSourceType()) + "\""
                        + ",\"snippet\":\"" + safeJson(result.getSnippet()) + "\""
                        + ",\"confidence\":\"" + safeJson(result.getConfidence()) + "\""
                        + ",\"accessChecked\":" + Boolean.TRUE.equals(result.getAccessChecked()) + "}")
                .toList();
        return "[" + String.join(",", sources) + "]";
    }

    private String toMemoriesJson(List<AgentMemory> memories) {
        List<String> memoryItems = memories.stream()
                .map(memory -> "{\"memoryId\":" + memory.getId()
                        + ",\"memoryType\":\"" + safeJson(memory.getMemoryType()) + "\""
                        + ",\"memoryScope\":\"" + safeJson(memory.getMemoryScope()) + "\""
                        + ",\"confidence\":\"" + safeJson(memory.getConfidence()) + "\""
                        + ",\"summaryText\":\"" + safeJson(memory.getSummaryText()) + "\"}")
                .toList();
        return "[" + String.join(",", memoryItems) + "]";
    }

    private String knowledgeConfidence(List<KnowledgeRetrieveResult> results) {
        if (results.isEmpty()) {
            return "insufficient";
        }
        if (results.stream().anyMatch(result -> "conflicting".equals(result.getConfidence()))) {
            return "conflicting";
        }
        if (results.stream().anyMatch(result -> "high".equals(result.getConfidence()))) {
            return "high";
        }
        if (results.stream().anyMatch(result -> "medium".equals(result.getConfidence()))) {
            return "medium";
        }
        if (results.stream().anyMatch(result -> "low".equals(result.getConfidence()))) {
            return "low";
        }
        return "insufficient";
    }

    private boolean isLowConfidence(List<KnowledgeRetrieveResult> results) {
        String confidence = knowledgeConfidence(results);
        return "low".equals(confidence)
                || "conflicting".equals(confidence)
                || "insufficient".equals(confidence);
    }

    private boolean isToolAllowedByAgentVersion(RunStartCommand command, Long toolId, String toolCode) {
        if (toolId == null || command.getAgentVersionId() == null || agentVersionService == null) {
            return true;
        }
        AgentVersion version = agentVersionService.getVersion(command.getAgentVersionId());
        ToolScope toolScope = parseToolScope(version.getToolScopeJson());
        if (toolScope.isEmpty()) {
            return true;
        }
        return toolScope.toolIds().contains(toolId)
                || (StringUtils.hasText(toolCode) && toolScope.toolCodes().contains(toolCode));
    }

    private ToolScope parseToolScope(String toolScopeJson) {
        if (!StringUtils.hasText(toolScopeJson)) {
            return ToolScope.empty();
        }
        try {
            JsonNode scope = objectMapper.readTree(toolScopeJson);
            if (!scope.isArray()) {
                return ToolScope.empty();
            }
            Set<Long> toolIds = ConcurrentHashMap.newKeySet();
            Set<String> toolCodes = ConcurrentHashMap.newKeySet();
            for (JsonNode item : scope) {
                if (item.hasNonNull("toolId")) {
                    toolIds.add(item.get("toolId").asLong());
                }
                if (item.hasNonNull("toolCode") && StringUtils.hasText(item.get("toolCode").asText())) {
                    toolCodes.add(item.get("toolCode").asText());
                }
            }
            return new ToolScope(toolIds, toolCodes);
        } catch (Exception ignored) {
            return ToolScope.empty();
        }
    }

    private String buildProjectAssistantWorkflowSteps(JsonNode root, String assistantTaskType) {
        List<String> steps = new ArrayList<>();
        steps.add(workflowStepJson(1L, "plan", "生成执行计划"));
        long index = 2L;
        if (root.hasNonNull("knowledgeBaseId")) {
            steps.add(workflowStepJson(index++, "knowledge", "检索项目资料"));
        }
        if (root.hasNonNull("modelId")) {
            steps.add(workflowStepJson(index++, "model_call", "生成分析内容"));
        }
        if (root.hasNonNull("toolId")) {
            steps.add(workflowStepJson(index++, "tool_call", "执行受控工具"));
        }
        steps.add(workflowStepJson(index++, "artifact", "生成结构化交付物"));
        steps.add(workflowStepJson(index, "self_check", "规则自检"));
        return "{\"assistantTaskType\":\"" + safeJson(assistantTaskType)
                + "\",\"steps\":[" + String.join(",", steps) + "]}";
    }

    private String workflowStepJson(Long stepId, String stepType, String stepName) {
        return "{\"stepId\":" + stepId
                + ",\"stepType\":\"" + safeJson(stepType)
                + "\",\"loopPhase\":\"" + loopPhaseForStepType(stepType).getCode()
                + "\",\"stepName\":\"" + safeJson(stepName) + "\"}";
    }

    private void recordStepStarted(RunStartCommand command,
                                   Long stepId,
                                   String stepType,
                                   String stepName,
                                   String assistantTaskType) {
        record(command, stepId, "STEP_STARTED", stepName + " started",
                "{\"stepType\":\"" + safeJson(stepType)
                        + "\",\"loopPhase\":\"" + loopPhaseForStepType(stepType).getCode()
                        + "\",\"stepName\":\"" + safeJson(stepName)
                        + "\",\"assistantTaskType\":\"" + safeJson(assistantTaskType) + "\"}");
    }

    private void recordStepCompleted(RunStartCommand command,
                                     Long stepId,
                                     String stepType,
                                     String stepName,
                                     String assistantTaskType,
                                     String status) {
        record(command, stepId, "STEP_COMPLETED", stepName + " completed",
                "{\"stepType\":\"" + safeJson(stepType)
                        + "\",\"loopPhase\":\"" + loopPhaseForStepType(stepType).getCode()
                        + "\",\"stepName\":\"" + safeJson(stepName)
                        + "\",\"assistantTaskType\":\"" + safeJson(assistantTaskType)
                        + "\",\"status\":\"" + safeJson(status) + "\"}");
    }

    private void recordAgentLoopObserved(RunStartCommand command,
                                         String assistantTaskType,
                                         JsonNode root,
                                         List<AgentMemory> memories) {
        JsonNode collaborationContext = root == null ? null : root.path("collaborationContext");
        JsonNode inputArtifactIds = collaborationContext == null ? null : collaborationContext.path("inputArtifactIds");
        int inputArtifactCount = inputArtifactIds != null && inputArtifactIds.isArray()
                ? inputArtifactIds.size()
                : collaborationContext != null && collaborationContext.hasNonNull("inputArtifactId") ? 1 : 0;
        record(command, "AGENT_LOOP_OBSERVED", "Agent observed task context",
                "{\"loopPhase\":\"" + AgentLoopPhase.OBSERVE.getCode()
                        + "\",\"assistantTaskType\":\"" + safeJson(assistantTaskType)
                        + "\",\"inputArtifactCount\":" + inputArtifactCount
                        + ",\"memoryCount\":" + (memories == null ? 0 : memories.size()) + "}");
    }

    private AgentLoopPhase loopPhaseForStepType(String stepType) {
        if ("plan".equals(stepType)) {
            return AgentLoopPhase.PLAN;
        }
        if ("artifact".equals(stepType)) {
            return AgentLoopPhase.ARTIFACT;
        }
        if ("self_check".equals(stepType)) {
            return AgentLoopPhase.REFLECT;
        }
        return AgentLoopPhase.ACT;
    }

    private void record(RunStartCommand command, String eventType, String summary, String payloadJson) {
        record(command, null, eventType, summary, payloadJson);
    }

    private void record(RunStartCommand command, Long stepId, String eventType, String summary, String payloadJson) {
        eventCache.computeIfAbsent(command.getRunId(), key -> new ArrayList<>()).add(RuntimeEvent.builder()
                .tenantId(command.getTenantId())
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .stepId(stepId)
                .traceId(command.getTraceId())
                .eventType(eventType)
                .eventSummary(summary)
                .payloadJson(payloadJson)
                .occurredAt(OffsetDateTime.now())
                .build());
    }

    private boolean isApprovalApproved(Long runId, Long approvalRequestId) {
        return approvalRequestId != null
                && approvedApprovalRequests.getOrDefault(runId, Set.of()).contains(approvalRequestId);
    }

    private void saveCheckpoint(RunStartCommand command,
                                JsonNode root,
                                Long toolId,
                                String callPayloadJson,
                                Long approvalRequestId,
                                String toolType,
                                String toolCode,
                                String riskLevel) {
        saveCheckpoint(command, root, toolId, callPayloadJson, approvalRequestId, toolType, toolCode, riskLevel, null);
    }

    private void saveCheckpoint(RunStartCommand command,
                                JsonNode root,
                                Long toolId,
                                String callPayloadJson,
                                Long approvalRequestId,
                                String toolType,
                                String toolCode,
                                String riskLevel,
                                Integer toolCallIndex) {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setTenantId(command.getTenantId());
        checkpoint.setTaskId(command.getTaskId());
        checkpoint.setRunId(command.getRunId());
        checkpoint.setCheckpointType("tool_call");
        checkpoint.setCheckpointStatus("suspended");
        checkpoint.setApprovalRequestId(approvalRequestId);
        checkpoint.setPayloadJson(buildCheckpointPayload(command, root, toolId, callPayloadJson, toolType, toolCode, riskLevel, toolCallIndex));
        checkpoint.setResumePayloadJson("{}");
        runtimeCheckpointService.save(checkpoint);
    }

    private void approveCheckpoint(RunApprovalResultCommand command) {
        RuntimeCheckpoint checkpoint = runtimeCheckpointService.getOne(new LambdaQueryWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getTenantId, command.getTenantId())
                .eq(RuntimeCheckpoint::getRunId, command.getRunId())
                .eq(RuntimeCheckpoint::getApprovalRequestId, command.getApprovalRequestId())
                .eq(RuntimeCheckpoint::getCheckpointStatus, "suspended")
                .last("limit 1"));
        if (checkpoint == null) {
            return;
        }
        checkpoint.setCheckpointStatus("approved");
        checkpoint.setResumePayloadJson("{\"approvalRequestId\":" + command.getApprovalRequestId()
                + ",\"approvalStatus\":\"" + safeJson(command.getApprovalStatus()) + "\"}");
        runtimeCheckpointService.updateById(checkpoint);
    }

    private void completeCheckpoint(Long runId, Long approvalRequestId) {
        RuntimeCheckpoint checkpoint = runtimeCheckpointService.getOne(new LambdaQueryWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getRunId, runId)
                .eq(RuntimeCheckpoint::getApprovalRequestId, approvalRequestId)
                .eq(RuntimeCheckpoint::getCheckpointStatus, "resuming")
                .last("limit 1"));
        if (checkpoint == null) {
            return;
        }
        checkpoint.setCheckpointStatus("completed");
        runtimeCheckpointService.updateById(checkpoint);
    }

    private SuspendedToolCall loadSuspendedToolCall(RunResumeCommand command) {
        RuntimeCheckpoint checkpoint = runtimeCheckpointService.getOne(new LambdaQueryWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getTenantId, command.getTenantId())
                .eq(RuntimeCheckpoint::getRunId, command.getRunId())
                .eq(RuntimeCheckpoint::getCheckpointType, "tool_call")
                .eq(RuntimeCheckpoint::getCheckpointStatus, "approved")
                .last("limit 1"));
        if (checkpoint == null) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(checkpoint.getPayloadJson());
            JsonNode root = objectMapper.readTree(payload.path("rootJson").asText("{}"));
            RunStartCommand startCommand = RunStartCommand.builder()
                    .tenantId(checkpoint.getTenantId())
                    .userId(readNullableLong(payload, "userId"))
                    .agentId(readNullableLong(payload, "agentId"))
                    .agentVersionId(readNullableLong(payload, "agentVersionId"))
                    .taskId(checkpoint.getTaskId())
                    .runId(checkpoint.getRunId())
                    .traceId(payload.path("traceId").asText(null))
                    .runtimeSnapshotJson("{}")
                    .build();
            approvedApprovalRequests.computeIfAbsent(command.getRunId(), key -> ConcurrentHashMap.newKeySet())
                    .add(checkpoint.getApprovalRequestId());
            return new SuspendedToolCall(
                    startCommand,
                    root,
                    payload.path("toolId").asLong(),
                    payload.path("callPayloadJson").asText("{}"),
                    checkpoint.getApprovalRequestId(),
                    payload.path("toolType").asText(null),
                    payload.path("toolCode").asText(null),
                    payload.path("riskLevel").asText(null),
                    payload.hasNonNull("toolCallIndex") ? payload.path("toolCallIndex").asInt() : null,
                    true
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildCheckpointPayload(RunStartCommand command,
                                          JsonNode root,
                                          Long toolId,
                                          String callPayloadJson,
                                          String toolType,
                                          String toolCode,
                                          String riskLevel) {
        return buildCheckpointPayload(command, root, toolId, callPayloadJson, toolType, toolCode, riskLevel, null);
    }

    private String buildCheckpointPayload(RunStartCommand command,
                                          JsonNode root,
                                          Long toolId,
                                          String callPayloadJson,
                                          String toolType,
                                          String toolCode,
                                          String riskLevel,
                                          Integer toolCallIndex) {
        return "{\"userId\":" + command.getUserId()
                + ",\"agentId\":" + command.getAgentId()
                + ",\"agentVersionId\":" + command.getAgentVersionId()
                + ",\"traceId\":\"" + safeJson(command.getTraceId()) + "\""
                + ",\"toolId\":" + toolId
                + toolCallIndexJson(toolCallIndex)
                + toolMetadataJson(toolType, toolCode, riskLevel)
                + ",\"callPayloadJson\":\"" + safeJson(callPayloadJson) + "\""
                + ",\"rootJson\":\"" + safeJson(root.toString()) + "\"}";
    }

    private Long readNullableLong(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) ? node.get(fieldName).asLong() : null;
    }

    private Long readNestedNullableLong(JsonNode node, String objectFieldName, String fieldName) {
        if (node == null || !node.hasNonNull(objectFieldName) || !node.get(objectFieldName).isObject()) {
            return null;
        }
        return readNullableLong(node.get(objectFieldName), fieldName);
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private boolean claimCheckpointIfNeeded(SuspendedToolCall suspendedToolCall) {
        if (!suspendedToolCall.requiresPersistentClaim()) {
            return true;
        }
        return runtimeCheckpointService.claimApprovedCheckpoint(
                suspendedToolCall.startCommand().getTenantId(),
                suspendedToolCall.startCommand().getRunId(),
                suspendedToolCall.approvalRequestId()
        );
    }

    private ToolMetadata toolMetadata(JsonNode root) {
        return new ToolMetadata(readText(root, "toolType"), readText(root, "toolCode"), readText(root, "riskLevel"));
    }

    private ToolMetadata toolMetadata(JsonNode root, JsonNode toolNode) {
        return new ToolMetadata(
                firstNonBlank(readText(toolNode, "toolType"), readText(root, "toolType")),
                firstNonBlank(readText(toolNode, "toolCode"), readText(root, "toolCode")),
                firstNonBlank(readText(toolNode, "riskLevel"), readText(root, "riskLevel"))
        );
    }

    private String toolMetadataJson(ToolMetadata metadata) {
        return toolMetadataJson(metadata.toolType(), metadata.toolCode(), metadata.riskLevel());
    }

    private String toolMetadataJson(ToolCallExecuteResponse response) {
        return toolMetadataJson(response.getToolType(), response.getToolCode(), response.getRiskLevel());
    }

    private String toolMetadataJson(String toolType, String toolCode, String riskLevel) {
        StringBuilder json = new StringBuilder();
        if (StringUtils.hasText(toolType)) {
            json.append(",\"toolType\":\"").append(safeJson(toolType)).append("\"")
                    .append(",\"executorType\":\"").append(safeJson(toolType)).append("\"");
        }
        if (StringUtils.hasText(toolCode)) {
            json.append(",\"toolCode\":\"").append(safeJson(toolCode)).append("\"");
        }
        if (StringUtils.hasText(riskLevel)) {
            json.append(",\"riskLevel\":\"").append(safeJson(riskLevel)).append("\"");
        }
        return json.toString();
    }

    private boolean hasToolExecution(JsonNode root) {
        return root != null && (root.hasNonNull("toolId") || hasToolCalls(root));
    }

    private boolean hasToolCalls(JsonNode root) {
        JsonNode toolCalls = root == null ? null : root.path("toolCalls");
        return toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty();
    }

    private String toolCallIndexJson(Integer toolCallIndex) {
        return toolCallIndex == null ? "" : ",\"toolCallIndex\":" + toolCallIndex;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private record SuspendedToolCall(RunStartCommand startCommand,
                                     JsonNode root,
                                     Long toolId,
                                     String callPayloadJson,
                                     Long approvalRequestId,
                                     String toolType,
                                     String toolCode,
                                     String riskLevel,
                                     Integer toolCallIndex,
                                     boolean requiresPersistentClaim) {
    }

    private record ToolMetadata(String toolType, String toolCode, String riskLevel) {
    }

    private record ToolScope(Set<Long> toolIds, Set<String> toolCodes) {
        private static ToolScope empty() {
            return new ToolScope(Set.of(), Set.of());
        }

        private boolean isEmpty() {
            return toolIds.isEmpty() && toolCodes.isEmpty();
        }
    }

    private record ArtifactPayload(String artifactType, String artifactName, String contentText) {
    }
}
