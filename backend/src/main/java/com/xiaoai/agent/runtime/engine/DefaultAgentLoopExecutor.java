package com.xiaoai.agent.runtime.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.skill.entity.Skill;
import com.xiaoai.agent.skill.executor.SkillExecutor;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认 Agent 循环执行器实现
 * 实现 observe → plan → act → reflect 循环
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public class DefaultAgentLoopExecutor implements AgentLoopExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentLoopExecutor.class);
    private static final int MAX_LOOP_ITERATIONS = 5;
    private static final long MAX_EXECUTION_TIME_MS = 300_000; // 5 minutes

    private final ModelGateway modelGateway;
    private final ToolConfigService toolConfigService;
    private final AgentVersionService agentVersionService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final AgentMemoryService agentMemoryService;
    private final SkillExecutor skillExecutor;
    private final ContextBuilder contextBuilder;
    private final ObjectMapper objectMapper;
    private final Map<Long, List<RuntimeEvent>> eventCache;

    public DefaultAgentLoopExecutor(ModelGateway modelGateway,
                                    ToolConfigService toolConfigService,
                                    AgentVersionService agentVersionService,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    AgentMemoryService agentMemoryService,
                                    SkillExecutor skillExecutor,
                                    ObjectMapper objectMapper,
                                    Map<Long, List<RuntimeEvent>> eventCache) {
        this.modelGateway = modelGateway;
        this.toolConfigService = toolConfigService;
        this.agentVersionService = agentVersionService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.agentMemoryService = agentMemoryService;
        this.skillExecutor = skillExecutor;
        this.contextBuilder = new ContextBuilder(agentVersionService, agentMemoryService, objectMapper);
        this.objectMapper = objectMapper;
        this.eventCache = eventCache;
    }

    /**
     * 执行Agent循环
     * <p>
     * 实现 observe → plan → act → reflect 四阶段循环，让模型驱动执行流程。
     * 循环最多执行5次迭代，总执行时间不超过5分钟。
     * </p>
     *
     * @param command 运行启动命令，包含任务ID、运行ID、租户ID、用户ID、Agent版本等信息
     * @param context 上下文包，包含输入文本、上下文文件、Agent版本快照等执行上下文
     * @return AgentLoopResult 执行结果，包含：
     *         - taskId: 任务ID
     *         - runId: 运行ID
     *         - finalStatus: 最终状态（success/failed/cancelled/timeout）
     *         - resultSummary: 结果摘要
     *         - events: 执行过程中产生的所有事件列表
     *         - loopCount: 实际循环次数
     *         - elapsedMs: 执行耗时（毫秒）
     */
    @Override
    public AgentLoopResult execute(RunStartCommand command, ContextPackage context) {
        long startTime = System.currentTimeMillis();
        List<RuntimeEvent> events = new ArrayList<>();
        int loopCount = 0;
        String finalStatus = "success";
        String resultSummary = null;

        try {
            // 初始化上下文
            AgentLoopContext loopContext = initializeLoopContext(command, context);
            recordEvent(command, events, "LOOP_STARTED", "Agent loop started",
                    buildLoopStartedPayload(loopContext));

            // 主循环
            while (loopCount < MAX_LOOP_ITERATIONS) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > MAX_EXECUTION_TIME_MS) {
                    finalStatus = "timeout";
                    resultSummary = "Execution exceeded maximum time limit";
                    recordEvent(command, events, "LOOP_TIMEOUT", "Agent loop timeout",
                            "{\"elapsedMs\":" + elapsed + ",\"maxMs\":" + MAX_EXECUTION_TIME_MS + "}");
                    break;
                }

                loopCount++;
                recordEvent(command, events, "LOOP_ITERATION", "Starting loop iteration " + loopCount,
                        "{\"iteration\":" + loopCount + ",\"maxIterations\":" + MAX_LOOP_ITERATIONS + "}");

                // Phase 1: Observe - 收集当前上下文
                observe(command, events, loopContext);

                // Phase 1.5: Skill Retrieve - 检索匹配的技能（仅首次迭代执行）
                if (loopCount == 1) {
                    retrieveSkills(command, events, loopContext);
                }

                // Phase 2: Plan - 让模型生成执行计划
                ExecutionPlan plan = plan(command, events, loopContext);
                if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
                    finalStatus = "failed";
                    resultSummary = "Model failed to generate execution plan";
                    recordEvent(command, events, "PLAN_FAILED", "Failed to generate execution plan", "{}");
                    break;
                }

                recordEvent(command, events, "PLAN_GENERATED", "Execution plan generated",
                        buildPlanPayload(plan));

                // Phase 3: Act - 执行计划中的步骤
                List<StepResult> stepResults = act(command, events, loopContext, plan);

                // Phase 4: Reflect - 评估执行结果
                ReflectionResult reflection = reflect(command, events, loopContext, plan, stepResults);

                // Phase 5: Skill Feedback - 记录技能使用反馈
                if (!loopContext.getMatchedSkills().isEmpty()) {
                    recordSkillFeedback(command, events, loopContext, reflection);
                }

                // 更新上下文
                loopContext.addIterationResults(loopCount, stepResults, reflection);

                // 检查是否需要继续循环
                if (reflection.isComplete()) {
                    resultSummary = reflection.getSummary();
                    recordEvent(command, events, "LOOP_COMPLETED", "Agent loop completed",
                            "{\"iterations\":" + loopCount + ",\"summary\":\"" + safeJson(resultSummary) + "\"}");
                    break;
                }

                // 如果模型建议调整计划，继续循环
                if (reflection.needsAdjustment()) {
                    recordEvent(command, events, "PLAN_ADJUSTMENT", "Model suggests plan adjustment",
                            "{\"reason\":\"" + safeJson(reflection.getAdjustmentReason()) + "\"}");
                }
            }

            if (loopCount >= MAX_LOOP_ITERATIONS && resultSummary == null) {
                finalStatus = "failed";
                resultSummary = "Exceeded maximum loop iterations";
            }

        } catch (Exception e) {
            log.error("Agent loop execution failed", e);
            finalStatus = "failed";
            resultSummary = "Execution failed: " + e.getMessage();
            recordEvent(command, events, "LOOP_ERROR", "Agent loop error",
                    "{\"error\":\"" + safeJson(e.getMessage()) + "\"}");
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        return AgentLoopResult.builder()
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .finalStatus(finalStatus)
                .resultSummary(resultSummary)
                .events(events)
                .loopCount(loopCount)
                .elapsedMs(elapsedMs)
                .build();
    }

    /**
     * 初始化循环上下文
     * <p>
     * 创建AgentLoopContext对象，设置基本的执行上下文信息，包括：
     * - 输入文本
     * - 租户ID、用户ID、Agent ID、Agent版本ID
     * - 加载已确认的记忆（最多5条task范围记忆）
     * </p>
     *
     * @param command 运行启动命令
     * @param context 上下文包
     * @return 初始化后的循环上下文对象
     */
    private AgentLoopContext initializeLoopContext(RunStartCommand command, ContextPackage context) {
        AgentLoopContext loopContext = new AgentLoopContext();
        loopContext.setInputText(context.getInputText());
        loopContext.setTenantId(command.getTenantId());
        loopContext.setUserId(command.getUserId());
        loopContext.setAgentId(command.getAgentId());
        loopContext.setAgentVersionId(command.getAgentVersionId());

        // 加载记忆
        if (agentMemoryService != null) {
            List<AgentMemory> memories = agentMemoryService.listConfirmedMemoriesForRuntime(
                    command.getTenantId(),
                    command.getAgentId(),
                    command.getTaskId(),
                    null, // sessionId
                    command.getUserId(),
                    List.of("task"),
                    5
            );
            loopContext.setMemories(memories);
        }

        return loopContext;
    }

    /**
     * Phase 1: Observe - 收集当前上下文信息
     * <p>
     * 从之前的迭代结果中提取知识片段和工具调用结果，构建当前上下文：
     * - 提取所有knowledge_retrieve类型的步骤结果，构建知识上下文列表
     * - 提取所有tool_call类型的步骤结果，构建工具调用上下文列表
     * - 将收集到的上下文信息设置到循环上下文中
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param context 循环上下文
     */
    private void observe(RunStartCommand command, List<RuntimeEvent> events, AgentLoopContext context) {
        recordEvent(command, events, "OBSERVE_STARTED", "Observe phase started", "{}");

        // 收集的知识片段
        List<KnowledgeContext> knowledgeContexts = new ArrayList<>();
        // 收集的工具调用结果
        List<ToolCallContext> toolCallContexts = new ArrayList<>();

        // 如果有之前的步骤结果，提取相关信息
        for (IterationResult iteration : context.getIterationResults()) {
            for (StepResult stepResult : iteration.getStepResults()) {
                if ("knowledge_retrieve".equals(stepResult.getStepType()) && stepResult.getKnowledgeResults() != null) {
                    for (KnowledgeRetrieveResult kr : stepResult.getKnowledgeResults()) {
                        knowledgeContexts.add(new KnowledgeContext(
                                kr.getDocumentId(),
                                kr.getChunkId(),
                                kr.getChunkText(),
                                kr.getSourceTitle(),
                                kr.getConfidence()
                        ));
                    }
                } else if ("tool_call".equals(stepResult.getStepType())) {
                    toolCallContexts.add(new ToolCallContext(
                            stepResult.getToolId(),
                            stepResult.getToolCode(),
                            stepResult.getResultJson(),
                            stepResult.getStatus()
                    ));
                }
            }
        }

        context.setKnowledgeContexts(knowledgeContexts);
        context.setToolCallContexts(toolCallContexts);

        recordEvent(command, events, "OBSERVE_COMPLETED", "Observe phase completed",
                "{\"knowledgeCount\":" + knowledgeContexts.size()
                        + ",\"toolCallCount\":" + toolCallContexts.size()
                        + ",\"memoryCount\":" + (context.getMemories() != null ? context.getMemories().size() : 0) + "}");
    }

    /**
     * Phase 2: Plan - 让模型生成执行计划
     * <p>
     * 构建分层上下文，调用模型生成执行计划：
     * - 使用ContextBuilder构建包含知识、工具调用结果、迭代结果的分层上下文
     * - 构建plan prompt，包含当前上下文和可用工具描述
     * - 调用模型生成执行计划（JSON格式）
     * - 解析模型返回的JSON为ExecutionPlan对象
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param context 循环上下文
     * @return 执行计划对象，如果生成失败则返回null
     */
    private ExecutionPlan plan(RunStartCommand command, List<RuntimeEvent> events, AgentLoopContext context) {
        recordEvent(command, events, "PLAN_STARTED", "Plan phase started", "{}");

        try {
            // 构建分层上下文
            LayeredContext layeredContext = contextBuilder.build(
                    command,
                    context.getKnowledgeContexts() != null ? context.getKnowledgeContexts().stream()
                            .map(kc -> LayeredContext.KnowledgeContext.builder()
                                    .documentId(kc.documentId())
                                    .chunkId(kc.chunkId())
                                    .chunkText(kc.chunkText())
                                    .sourceTitle(kc.sourceTitle())
                                    .confidence(kc.confidence())
                                    .build())
                            .collect(java.util.stream.Collectors.toList()) : List.of(),
                    context.getToolCallContexts() != null ? context.getToolCallContexts().stream()
                            .map(tc -> LayeredContext.ToolCallContext.builder()
                                    .toolId(tc.toolId())
                                    .toolCode(tc.toolCode())
                                    .resultJson(tc.resultJson())
                                    .status(tc.status())
                                    .build())
                            .collect(java.util.stream.Collectors.toList()) : List.of(),
                    context.getIterationResults() != null ? context.getIterationResults().stream()
                            .map(ir -> LayeredContext.IterationResult.builder()
                                    .iteration(ir.getIteration())
                                    .stepResults(ir.getStepResults() != null ? ir.getStepResults().stream()
                                            .map(sr -> LayeredContext.StepResult.builder()
                                                    .stepId(sr.getStepId())
                                                    .stepType(sr.getStepType())
                                                    .status(sr.getStatus())
                                                    .error(sr.getError())
                                                    .resultJson(sr.getResultJson())
                                                    .build())
                                            .collect(java.util.stream.Collectors.toList()) : List.of())
                                    .reflection(ir.getReflection() != null ? LayeredContext.ReflectionResult.builder()
                                            .complete(ir.getReflection().isComplete())
                                            .summary(ir.getReflection().getSummary())
                                            .needsAdjustment(ir.getReflection().needsAdjustment())
                                            .adjustmentReason(ir.getReflection().getAdjustmentReason())
                                            .build() : null)
                                    .build())
                            .collect(java.util.stream.Collectors.toList()) : List.of()
            );

            // 构建 plan prompt
            String planPrompt = buildPlanPrompt(context, layeredContext);

            // 调用模型生成计划
            ChatModelCommand modelCommand = new ChatModelCommand();
            modelCommand.setModelId(resolveModelId(command));
            modelCommand.setTaskId(command.getTaskId());
            modelCommand.setRunId(command.getRunId());
            modelCommand.setPrompt(planPrompt);

            ChatModelResponse response = modelGateway.chat(modelCommand);
            String planJson = response.getContent();

            recordEvent(command, events, "MODEL_CALL", "Model call for plan",
                    "{\"modelId\":" + modelCommand.getModelId()
                            + ",\"promptTokens\":" + response.getPromptTokens()
                            + ",\"completionTokens\":" + response.getCompletionTokens()
                            + ",\"totalTokens\":" + response.getTotalTokens() + "}");

            // 解析计划
            ExecutionPlan plan = parseExecutionPlan(planJson);

            recordEvent(command, events, "PLAN_COMPLETED", "Plan phase completed",
                    "{\"stepCount\":" + (plan != null && plan.getSteps() != null ? plan.getSteps().size() : 0) + "}");

            return plan;

        } catch (Exception e) {
            log.error("Plan phase failed", e);
            recordEvent(command, events, "PLAN_ERROR", "Plan phase error",
                    "{\"error\":\"" + safeJson(e.getMessage()) + "\"}");
            return null;
        }
    }

    /**
     * Phase 3: Act - 执行计划中的步骤
     * <p>
     * 按照执行计划中的步骤顺序执行：
     * - 遍历ExecutionPlan中的每个ExecutionStep
     * - 调用executeStep执行每个步骤
     * - 收集每个步骤的执行结果（StepResult）
     * - 如果某个必需步骤失败，终止后续步骤执行
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param context 循环上下文
     * @param plan 执行计划
     * @return 步骤执行结果列表
     */
    private List<StepResult> act(RunStartCommand command, List<RuntimeEvent> events,
                                 AgentLoopContext context, ExecutionPlan plan) {
        recordEvent(command, events, "ACT_STARTED", "Act phase started",
                "{\"stepCount\":" + plan.getSteps().size() + "}");

        List<StepResult> stepResults = new ArrayList<>();

        for (ExecutionPlan.ExecutionStep step : plan.getSteps()) {
            StepResult result = executeStep(command, events, context, step);
            stepResults.add(result);

            // 如果步骤失败且是必须的，终止执行
            if (!"success".equals(result.getStatus()) && step.isRequired()) {
                recordEvent(command, events, "STEP_FAILED_REQUIRED", "Required step failed, stopping execution",
                        "{\"stepId\":\"" + step.getStepId() + "\",\"stepType\":\"" + step.getStepType() + "\"}");
                break;
            }
        }

        recordEvent(command, events, "ACT_COMPLETED", "Act phase completed",
                "{\"executedSteps\":" + stepResults.size()
                        + ",\"successCount\":" + stepResults.stream().filter(r -> "success".equals(r.getStatus())).count()
                        + ",\"failedCount\":" + stepResults.stream().filter(r -> !"success".equals(r.getStatus())).count() + "}");

        return stepResults;
    }

    /**
     * 执行单个步骤
     * <p>
     * 根据步骤类型分发到对应的执行方法：
     * - knowledge_retrieve: 调用executeKnowledgeRetrieve执行知识检索
     * - tool_call: 调用executeToolCall执行工具调用
     * - model_call: 调用executeModelCall执行模型调用
     * - 其他类型: 标记为失败，返回未知步骤类型错误
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param context 循环上下文
     * @param step 执行步骤
     * @return 步骤执行结果
     */
    private StepResult executeStep(RunStartCommand command, List<RuntimeEvent> events,
                                   AgentLoopContext context, ExecutionPlan.ExecutionStep step) {
        recordEvent(command, events, "STEP_STARTED", "Step started",
                "{\"stepId\":\"" + step.getStepId() + "\",\"stepType\":\"" + step.getStepType()
                        + "\",\"description\":\"" + safeJson(step.getDescription()) + "\"}");

        StepResult result = new StepResult();
        result.setStepId(step.getStepId());
        result.setStepType(step.getStepType());

        try {
            switch (step.getStepType()) {
                case "knowledge_retrieve":
                    result = executeKnowledgeRetrieve(command, events, step);
                    break;
                case "tool_call":
                    result = executeToolCall(command, events, step);
                    break;
                case "model_call":
                    result = executeModelCall(command, events, context, step);
                    break;
                default:
                    result.setStatus("failed");
                    result.setError("Unknown step type: " + step.getStepType());
            }
        } catch (Exception e) {
            log.error("Step execution failed: {}", step.getStepId(), e);
            result.setStatus("failed");
            result.setError(e.getMessage());
        }

        recordEvent(command, events, "STEP_COMPLETED", "Step completed",
                "{\"stepId\":\"" + step.getStepId() + "\",\"status\":\"" + result.getStatus() + "\"}");

        return result;
    }

    /**
     * 执行知识检索步骤
     * <p>
     * 从指定的知识库中检索相关知识：
     * - 验证knowledgeBaseId和query参数是否提供
     * - 构建RetrieveKnowledgeCommand，设置知识库ID、查询文本、topK（默认5）
     * - 调用KnowledgeDocumentService.retrieve执行检索
     * - 将检索结果设置到StepResult中
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param step 执行步骤，包含knowledgeBaseId、query、topK等参数
     * @return 步骤执行结果，包含检索到的知识列表
     */
    private StepResult executeKnowledgeRetrieve(RunStartCommand command, List<RuntimeEvent> events,
                                                ExecutionPlan.ExecutionStep step) {
        StepResult result = new StepResult();
        result.setStepId(step.getStepId());
        result.setStepType("knowledge_retrieve");

        if (step.getKnowledgeBaseId() == null || !StringUtils.hasText(step.getQuery())) {
            result.setStatus("failed");
            result.setError("Missing knowledgeBaseId or query");
            return result;
        }

        RetrieveKnowledgeCommand retrieveCommand = new RetrieveKnowledgeCommand();
        retrieveCommand.setKnowledgeBaseId(step.getKnowledgeBaseId());
        retrieveCommand.setQuery(step.getQuery());
        retrieveCommand.setTopK(step.getTopK() != null ? step.getTopK() : 5);

        List<KnowledgeRetrieveResult> results = knowledgeDocumentService.retrieve(retrieveCommand);
        result.setStatus("success");
        result.setKnowledgeResults(results);

        recordEvent(command, events, "KNOWLEDGE_RETRIEVE", "Knowledge retrieved",
                "{\"knowledgeBaseId\":" + step.getKnowledgeBaseId()
                        + ",\"query\":\"" + safeJson(step.getQuery())
                        + "\",\"hitCount\":" + results.size() + "}");

        return result;
    }

    /**
     * 执行工具调用步骤
     * <p>
     * 调用指定的工具并执行：
     * - 验证toolId参数是否提供
     * - 构建ExecuteToolCallCommand，设置工具ID、任务ID、运行ID、用户ID、Agent版本ID、调用参数
     * - 调用ToolConfigService.executeToolCall执行工具
     * - 根据工具执行状态设置StepResult（success/blocked/failed）
     * - 如果工具需要审批，返回blocked状态和审批请求ID
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param step 执行步骤，包含toolId、callPayloadJson等参数
     * @return 步骤执行结果，包含工具执行结果或审批请求信息
     */
    private StepResult executeToolCall(RunStartCommand command, List<RuntimeEvent> events,
                                       ExecutionPlan.ExecutionStep step) {
        StepResult result = new StepResult();
        result.setStepId(step.getStepId());
        result.setStepType("tool_call");

        if (step.getToolId() == null) {
            result.setStatus("failed");
            result.setError("Missing toolId");
            return result;
        }

        ExecuteToolCallCommand toolCommand = new ExecuteToolCallCommand();
        toolCommand.setToolId(step.getToolId());
        toolCommand.setTaskId(command.getTaskId());
        toolCommand.setRunId(command.getRunId());
        toolCommand.setApplicantUserId(command.getUserId());
        toolCommand.setAgentVersionId(command.getAgentVersionId());
        toolCommand.setCallPayloadJson(step.getCallPayloadJson() != null ? step.getCallPayloadJson() : "{}");
        toolCommand.setDryRun(false);
        toolCommand.setApprovalBypassed(false);

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(toolCommand);

        result.setToolId(step.getToolId());
        result.setToolCode(response.getToolCode());

        if ("success".equals(response.getStatus())) {
            result.setStatus("success");
            result.setResultJson(response.getResultJson());
        } else if ("blocked".equals(response.getStatus())) {
            result.setStatus("blocked");
            result.setError("Tool call blocked, approval required");
            result.setApprovalRequestId(response.getApprovalRequestId());
        } else {
            result.setStatus("failed");
            result.setError(response.getErrorMessage());
        }

        recordEvent(command, events, "TOOL_CALL", "Tool call executed",
                "{\"toolId\":" + step.getToolId()
                        + ",\"toolCode\":\"" + safeJson(response.getToolCode())
                        + "\",\"status\":\"" + response.getStatus() + "\"}");

        return result;
    }

    /**
     * 执行模型调用步骤
     * <p>
     * 调用大语言模型生成响应：
     * - 验证modelId和prompt参数是否提供
     * - 使用enhancePromptWithContext增强prompt，加入知识、工具结果、记忆等上下文
     * - 构建ChatModelCommand，设置模型ID、任务ID、运行ID、增强后的prompt
     * - 调用ModelGateway.chat执行模型调用
     * - 将模型响应内容、token使用量设置到StepResult中
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param context 循环上下文，包含知识、工具结果等上下文信息
     * @param step 执行步骤，包含modelId、prompt等参数
     * @return 步骤执行结果，包含模型生成的内容和token使用统计
     */
    private StepResult executeModelCall(RunStartCommand command, List<RuntimeEvent> events,
                                        AgentLoopContext context, ExecutionPlan.ExecutionStep step) {
        StepResult result = new StepResult();
        result.setStepId(step.getStepId());
        result.setStepType("model_call");

        if (step.getModelId() == null || !StringUtils.hasText(step.getPrompt())) {
            result.setStatus("failed");
            result.setError("Missing modelId or prompt");
            return result;
        }

        // 增强 prompt，加入上下文
        String enhancedPrompt = enhancePromptWithContext(step.getPrompt(), context);

        ChatModelCommand modelCommand = new ChatModelCommand();
        modelCommand.setModelId(step.getModelId());
        modelCommand.setTaskId(command.getTaskId());
        modelCommand.setRunId(command.getRunId());
        modelCommand.setPrompt(enhancedPrompt);

        ChatModelResponse response = modelGateway.chat(modelCommand);

        result.setStatus("success");
        result.setResultJson(response.getContent());
        result.setPromptTokens(response.getPromptTokens());
        result.setCompletionTokens(response.getCompletionTokens());
        result.setTotalTokens(response.getTotalTokens());

        recordEvent(command, events, "MODEL_CALL", "Model call succeeded",
                "{\"modelId\":" + step.getModelId()
                        + ",\"promptTokens\":" + response.getPromptTokens()
                        + ",\"completionTokens\":" + response.getCompletionTokens()
                        + ",\"totalTokens\":" + response.getTotalTokens() + "}");

        return result;
    }

    /**
     * Phase 4: Reflect - 评估执行结果并抽取记忆
     * <p>
     * 调用模型评估当前迭代结果，决定是否继续循环：
     * - 构建reflect prompt，包含执行计划、步骤结果、知识来源等信息
     * - 调用模型生成反思结果（JSON格式），包含：
     *   - complete: 是否完成任务
     *   - summary: 结果总结
     *   - needsAdjustment: 是否需要调整计划
     *   - extractedMemories: 抽取的记忆列表
     *   - knowledgeSources: 使用的知识来源列表
     *   - lowConfidenceWarning: 低可信度警告
     * - 保存抽取的记忆到数据库
     * - 记录知识来源和低可信度警告事件
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表，用于记录执行过程
     * @param context 循环上下文
     * @param plan 执行计划
     * @param stepResults 步骤执行结果列表
     * @return 反思结果，包含是否完成、总结、抽取的记忆等
     */
    private ReflectionResult reflect(RunStartCommand command, List<RuntimeEvent> events,
                                     AgentLoopContext context, ExecutionPlan plan,
                                     List<StepResult> stepResults) {
        recordEvent(command, events, "REFLECT_STARTED", "Reflect phase started", "{}");

        try {
            // 构建 reflect prompt
            String reflectPrompt = buildReflectPrompt(context, plan, stepResults);

            // 调用模型评估结果
            ChatModelCommand modelCommand = new ChatModelCommand();
            modelCommand.setModelId(resolveModelId(command));
            modelCommand.setTaskId(command.getTaskId());
            modelCommand.setRunId(command.getRunId());
            modelCommand.setPrompt(reflectPrompt);

            ChatModelResponse response = modelGateway.chat(modelCommand);
            String reflectionJson = response.getContent();

            recordEvent(command, events, "MODEL_CALL", "Model call for reflection",
                    "{\"modelId\":" + modelCommand.getModelId()
                            + ",\"promptTokens\":" + response.getPromptTokens()
                            + ",\"completionTokens\":" + response.getCompletionTokens()
                            + ",\"totalTokens\":" + response.getTotalTokens() + "}");

            // 解析反思结果
            ReflectionResult reflection = parseReflectionResult(reflectionJson);

            // 保存抽取的记忆
            if (reflection.getExtractedMemories() != null && !reflection.getExtractedMemories().isEmpty()) {
                saveExtractedMemories(command, events, reflection.getExtractedMemories());
            }

            // 记录知识来源
            if (reflection.getKnowledgeSources() != null && !reflection.getKnowledgeSources().isEmpty()) {
                recordKnowledgeSources(command, events, reflection.getKnowledgeSources());
            }

            // 记录低可信度警告
            if (reflection.getLowConfidenceWarning() != null) {
                recordEvent(command, events, "LOW_CONFIDENCE_WARNING", "Low confidence warning",
                        "{\"warning\":\"" + safeJson(reflection.getLowConfidenceWarning()) + "\"}");
            }

            recordEvent(command, events, "REFLECT_COMPLETED", "Reflect phase completed",
                    "{\"isComplete\":" + reflection.isComplete()
                            + ",\"needsAdjustment\":" + reflection.needsAdjustment()
                            + ",\"extractedMemoryCount\":" + (reflection.getExtractedMemories() != null ? reflection.getExtractedMemories().size() : 0)
                            + ",\"knowledgeSourceCount\":" + (reflection.getKnowledgeSources() != null ? reflection.getKnowledgeSources().size() : 0) + "}");

            return reflection;

        } catch (Exception e) {
            log.error("Reflect phase failed", e);
            recordEvent(command, events, "REFLECT_ERROR", "Reflect phase error",
                    "{\"error\":\"" + safeJson(e.getMessage()) + "\"}");

            // 默认认为完成
            ReflectionResult reflection = new ReflectionResult();
            reflection.setComplete(true);
            reflection.setSummary("Reflection failed, assuming completion");
            return reflection;
        }
    }

    /**
     * Phase 1.5: Skill Retrieve - 检索匹配的技能
     * <p>
     * 在首次迭代中，根据用户输入匹配相关技能，将技能内容注入到上下文中。
     * 匹配到的技能会在 plan 阶段作为参考信息注入到 prompt 中。
     * </p>
     */
    private void retrieveSkills(RunStartCommand command, List<RuntimeEvent> events, AgentLoopContext context) {
        if (skillExecutor == null) {
            return;
        }

        try {
            List<Skill> matchedSkills = skillExecutor.matchSkills(context.getTenantId(), context.getInputText());

            if (!matchedSkills.isEmpty()) {
                context.setMatchedSkills(matchedSkills);
                String skillContextText = skillExecutor.buildSkillContext(matchedSkills);
                context.setSkillContextText(skillContextText);

                recordEvent(command, events, "SKILLS_RETRIEVED", "Matched skills retrieved",
                        "{\"skillCount\":" + matchedSkills.size()
                                + ",\"skills\":[" + matchedSkills.stream()
                                .map(s -> "\"{\\\"code\\\":\\\"" + safeJson(s.getSkillCode())
                                        + "\\\",\\\"type\\\":\\\"" + safeJson(s.getSkillType()) + "\\\"}\"")
                                .collect(Collectors.joining(","))
                                + "]}");

                log.info("Skills retrieved: count={}, taskId={}", matchedSkills.size(), command.getTaskId());
            }
        } catch (Exception e) {
            log.error("Failed to retrieve skills", e);
            recordEvent(command, events, "SKILL_RETRIEVE_ERROR", "Skill retrieval failed",
                    "{\"error\":\"" + safeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Phase 5: Skill Feedback - 记录技能使用反馈
     * <p>
     * 在 reflect 阶段完成后，根据任务是否成功记录技能使用反馈。
     * 用于更新技能的成功率和使用统计，驱动技能自动改进。
     * </p>
     */
    private void recordSkillFeedback(RunStartCommand command, List<RuntimeEvent> events,
                                     AgentLoopContext context, ReflectionResult reflection) {
        if (skillExecutor == null || context.getMatchedSkills().isEmpty()) {
            return;
        }

        try {
            boolean taskSuccess = reflection.isComplete() && !reflection.needsAdjustment();
            skillExecutor.recordSkillFeedback(context.getMatchedSkills(), taskSuccess);

            recordEvent(command, events, "SKILL_FEEDBACK_RECORDED", "Skill usage feedback recorded",
                    "{\"skillCount\":" + context.getMatchedSkills().size()
                            + ",\"taskSuccess\":" + taskSuccess + "}");
        } catch (Exception e) {
            log.error("Failed to record skill feedback", e);
        }
    }

    /**
     * 构建 plan prompt
     * <p>
     * 构建用于生成执行计划的提示词，包含以下信息：
     * 1. 系统prompt（Agent角色、职责、边界）
     * 2. 用户需求文本
     * 3. 可用工具列表（工具ID、代码、名称、风险等级、描述、参数Schema）
     * 4. 已确认的记忆列表
     * 5. 之前的迭代结果（如果有的话）
     * 6. 执行计划的JSON格式要求
     * </p>
     *
     * @param context 循环上下文，包含用户需求、记忆等信息
     * @param layeredContext 分层上下文，包含系统prompt、知识、工具结果等
     * @return 构建好的prompt字符串
     */
    private String buildPlanPrompt(AgentLoopContext context, LayeredContext layeredContext) {
        StringBuilder prompt = new StringBuilder();

        // 1. 加入系统 prompt（Agent 角色、职责、边界）
        if (layeredContext != null && StringUtils.hasText(layeredContext.getSystemPrompt())) {
            prompt.append(layeredContext.getSystemPrompt()).append("\n\n");
        }

        prompt.append("你需要根据用户需求制定执行计划。\n\n");
        prompt.append("用户需求：").append(context.getInputText()).append("\n\n");

        // 2. 加入可用工具描述
        List<ToolDescription> availableTools = getAvailableToolDescriptions(context);
        if (!availableTools.isEmpty()) {
            prompt.append("可用工具列表：\n");
            for (ToolDescription tool : availableTools) {
                prompt.append("- 工具ID: ").append(tool.getToolId()).append("\n");
                prompt.append("  工具代码: ").append(tool.getToolCode()).append("\n");
                prompt.append("  工具名称: ").append(tool.getToolName()).append("\n");
                prompt.append("  风险等级: ").append(tool.getRiskLevel()).append("\n");
                if (tool.getDescription() != null) {
                    prompt.append("  描述: ").append(tool.getDescription()).append("\n");
                }
                if (tool.getParametersSchema() != null) {
                    prompt.append("  参数Schema: ").append(tool.getParametersSchema()).append("\n");
                }
                prompt.append("\n");
            }
        }

        // 3. 加入记忆
        if (layeredContext != null && layeredContext.getMemories() != null && !layeredContext.getMemories().isEmpty()) {
            prompt.append("已确认记忆：\n");
            for (AgentMemory memory : layeredContext.getMemories()) {
                prompt.append("- ").append(memory.getSummaryText()).append("\n");
            }
            prompt.append("\n");
        } else if (context.getMemories() != null && !context.getMemories().isEmpty()) {
            prompt.append("已确认记忆：\n");
            for (AgentMemory memory : context.getMemories()) {
                prompt.append("- ").append(memory.getSummaryText()).append("\n");
            }
            prompt.append("\n");
        }

        // 3.5 加入匹配的技能上下文
        if (StringUtils.hasText(context.getSkillContextText())) {
            prompt.append(context.getSkillContextText()).append("\n");
        }

        // 4. 加入之前的迭代结果
        if (layeredContext != null && layeredContext.getTaskContext() != null
                && layeredContext.getTaskContext().getIterationResults() != null
                && !layeredContext.getTaskContext().getIterationResults().isEmpty()) {
            prompt.append("之前的执行结果：\n");
            for (LayeredContext.IterationResult iteration : layeredContext.getTaskContext().getIterationResults()) {
                prompt.append("迭代 ").append(iteration.getIteration()).append("：\n");
                for (LayeredContext.StepResult result : iteration.getStepResults()) {
                    prompt.append("  - 步骤 ").append(result.getStepId())
                            .append(" (").append(result.getStepType()).append("): ")
                            .append(result.getStatus()).append("\n");
                }
            }
            prompt.append("\n");
        } else if (!context.getIterationResults().isEmpty()) {
            prompt.append("之前的执行结果：\n");
            for (IterationResult iteration : context.getIterationResults()) {
                prompt.append("迭代 ").append(iteration.getIteration()).append("：\n");
                for (StepResult result : iteration.getStepResults()) {
                    prompt.append("  - 步骤 ").append(result.getStepId())
                            .append(" (").append(result.getStepType()).append("): ")
                            .append(result.getStatus()).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("请生成一个JSON格式的执行计划，包含以下字段：\n");
        prompt.append("{\n");
        prompt.append("  \"goal\": \"执行目标\",\n");
        prompt.append("  \"outputType\": \"预期输出类型\",\n");
        prompt.append("  \"steps\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"stepId\": \"步骤ID\",\n");
        prompt.append("      \"stepType\": \"步骤类型(knowledge_retrieve/tool_call/model_call)\",\n");
        prompt.append("      \"description\": \"步骤描述\",\n");
        prompt.append("      \"required\": true/false,\n");
        prompt.append("      // 根据stepType填写以下字段之一：\n");
        prompt.append("      \"knowledgeBaseId\": 知识库ID,\n");
        prompt.append("      \"query\": \"检索查询\",\n");
        prompt.append("      \"topK\": 检索数量,\n");
        prompt.append("      \"toolId\": 工具ID（从可用工具列表中选择）,\n");
        prompt.append("      \"callPayloadJson\": \"工具调用参数JSON（符合参数Schema）\",\n");
        prompt.append("      \"modelId\": 模型ID,\n");
        prompt.append("      \"prompt\": \"模型提示词\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("只返回JSON，不要其他内容。");

        return prompt.toString();
    }

    /**
     * 构建 reflect prompt
     * <p>
     * 构建用于评估执行结果的提示词，包含以下信息：
     * 1. 用户需求文本
     * 2. 执行计划目标
     * 3. 每个步骤的执行结果（步骤ID、类型、状态、错误信息、结果摘要）
     * 4. 知识来源列表（来源标题、可信度）
     * 5. 反思结果的JSON格式要求，包括：
     *    - complete: 是否完成任务
     *    - summary: 结果总结
     *    - needsAdjustment: 是否需要调整计划
     *    - adjustmentReason: 调整原因
     *    - extractedMemories: 抽取的记忆列表
     *    - knowledgeSources: 使用的知识来源
     *    - lowConfidenceWarning: 低可信度警告
     * </p>
     *
     * @param context 循环上下文，包含用户需求等信息
     * @param plan 执行计划，包含计划目标
     * @param stepResults 步骤执行结果列表
     * @return 构建好的prompt字符串
     */
    private String buildReflectPrompt(AgentLoopContext context, ExecutionPlan plan,
                                      List<StepResult> stepResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能助手，需要评估执行结果并决定是否需要继续。\n\n");
        prompt.append("用户需求：").append(context.getInputText()).append("\n\n");
        prompt.append("执行计划：").append(plan.getGoal()).append("\n\n");
        prompt.append("执行结果：\n");

        // 收集知识来源信息
        List<KnowledgeContext> knowledgeSources = new ArrayList<>();
        for (StepResult result : stepResults) {
            if ("knowledge_retrieve".equals(result.getStepType()) && result.getKnowledgeResults() != null) {
                for (KnowledgeRetrieveResult kr : result.getKnowledgeResults()) {
                    knowledgeSources.add(new KnowledgeContext(
                            kr.getDocumentId(),
                            kr.getChunkId(),
                            kr.getChunkText(),
                            kr.getSourceTitle(),
                            kr.getConfidence()
                    ));
                }
            }
        }

        for (StepResult result : stepResults) {
            prompt.append("- 步骤 ").append(result.getStepId())
                    .append(" (").append(result.getStepType()).append("): ")
                    .append(result.getStatus());
            if (result.getError() != null) {
                prompt.append(" - 错误: ").append(result.getError());
            }
            if (result.getResultJson() != null) {
                String resultStr = result.getResultJson();
                if (resultStr.length() > 200) {
                    resultStr = resultStr.substring(0, 200) + "...";
                }
                prompt.append(" - 结果: ").append(resultStr);
            }
            prompt.append("\n");
        }

        // 添加知识来源信息
        if (!knowledgeSources.isEmpty()) {
            prompt.append("\n知识来源：\n");
            for (KnowledgeContext kc : knowledgeSources) {
                prompt.append("- 来源：").append(kc.sourceTitle() != null ? kc.sourceTitle() : "未知来源");
                prompt.append("，可信度：").append(kc.confidence() != null ? kc.confidence() : "unknown");
                prompt.append("\n");
            }
        }

        prompt.append("\n请评估执行结果，返回JSON格式：\n");
        prompt.append("{\n");
        prompt.append("  \"complete\": true/false, // 是否完成任务\n");
        prompt.append("  \"summary\": \"结果总结（如果使用了知识来源，请在总结中引用并注明来源）\",\n");
        prompt.append("  \"knowledgeSources\": [ // 使用的知识来源列表\n");
        prompt.append("    {\n");
        prompt.append("      \"sourceTitle\": \"来源标题\",\n");
        prompt.append("      \"confidence\": \"high/medium/low/unknown\",\n");
        prompt.append("      \"quoted\": \"引用的内容片段\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"lowConfidenceWarning\": \"如果存在低可信度来源，在此添加警告提示（否则为null）\",\n");
        prompt.append("  \"needsAdjustment\": true/false, // 是否需要调整计划\n");
        prompt.append("  \"adjustmentReason\": \"调整原因（如果需要）\",\n");
        prompt.append("  \"extractedMemories\": [ // 从本次执行中抽取的值得保存的记忆\n");
        prompt.append("    {\n");
        prompt.append("      \"memoryType\": \"decision/preference/constraint/fact\", // 记忆类型\n");
        prompt.append("      \"content\": \"记忆内容\",\n");
        prompt.append("      \"confidence\": \"high/medium/low\", // 置信度\n");
        prompt.append("      \"scope\": \"task/session/agent\" // 记忆范围\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("知识来源展示要求：\n");
        prompt.append("- 如果使用了知识来源，请在 summary 中明确引用并注明来源\n");
        prompt.append("- 在 knowledgeSources 中列出所有使用的来源及其可信度\n");
        prompt.append("- 如果存在低可信度（low/unknown）的来源，在 lowConfidenceWarning 中添加警告\n");
        prompt.append("- 警告格式：'注意：以下内容基于低可信度资料，请谨慎参考：[具体内容]'\n\n");
        prompt.append("记忆抽取说明：\n");
        prompt.append("- decision: 用户做出的重要决策\n");
        prompt.append("- preference: 用户表达的偏好\n");
        prompt.append("- constraint: 项目或任务的约束条件\n");
        prompt.append("- fact: 重要的事实信息\n");
        prompt.append("- 只抽取确实值得保存的记忆，不要抽取临时信息\n");
        prompt.append("- 如果没有值得保存的记忆，extractedMemories 返回空数组\n\n");
        prompt.append("只返回JSON，不要其他内容。");

        return prompt.toString();
    }

    /**
     * 增强 prompt，加入上下文
     * <p>
     * 将知识、工具调用结果、记忆等上下文信息追加到原始prompt中：
     * 1. 加入知识上下文（来源标题 + 文本内容）
     * 2. 加入工具调用结果（工具代码 + 状态 + 结果）
     * 3. 加入已确认的记忆列表
     * </p>
     *
     * @param prompt 原始prompt
     * @param context 循环上下文，包含知识、工具结果、记忆等信息
     * @return 增强后的prompt字符串
     */
    private String enhancePromptWithContext(String prompt, AgentLoopContext context) {
        StringBuilder enhanced = new StringBuilder(prompt);

        // 加入知识上下文
        if (context.getKnowledgeContexts() != null && !context.getKnowledgeContexts().isEmpty()) {
            enhanced.append("\n\n参考资料：\n");
            for (KnowledgeContext kc : context.getKnowledgeContexts()) {
                enhanced.append("- [").append(kc.sourceTitle()).append("] ")
                        .append(kc.chunkText()).append("\n");
            }
        }

        // 加入工具调用结果
        if (context.getToolCallContexts() != null && !context.getToolCallContexts().isEmpty()) {
            enhanced.append("\n\n工具调用结果：\n");
            for (ToolCallContext tc : context.getToolCallContexts()) {
                enhanced.append("- 工具 ").append(tc.toolCode()).append(": ")
                        .append(tc.status()).append("\n");
                if (tc.resultJson() != null) {
                    enhanced.append("  结果: ").append(tc.resultJson()).append("\n");
                }
            }
        }

        // 加入记忆
        if (context.getMemories() != null && !context.getMemories().isEmpty()) {
            enhanced.append("\n\n已确认记忆：\n");
            for (AgentMemory memory : context.getMemories()) {
                enhanced.append("- ").append(memory.getSummaryText()).append("\n");
            }
        }

        return enhanced.toString();
    }

    /**
     * 解析执行计划
     * <p>
     * 将模型返回的JSON字符串解析为ExecutionPlan对象：
     * 1. 使用extractJson提取JSON内容
     * 2. 使用ObjectMapper将JSON解析为ExecutionPlan对象
     * 3. 如果解析失败，记录错误日志并返回null
     * </p>
     *
     * @param planJson 模型返回的JSON字符串
     * @return 解析后的ExecutionPlan对象，如果解析失败则返回null
     */
    private ExecutionPlan parseExecutionPlan(String planJson) {
        try {
            // 尝试提取 JSON
            String json = extractJson(planJson);
            return objectMapper.readValue(json, ExecutionPlan.class);
        } catch (Exception e) {
            log.error("Failed to parse execution plan: {}", planJson, e);
            return null;
        }
    }

    /**
     * 解析反思结果
     * <p>
     * 将模型返回的JSON字符串解析为ReflectionResult对象：
     * 1. 使用extractJson提取JSON内容
     * 2. 使用ObjectMapper将JSON解析为ReflectionResult对象
     * 3. 如果解析失败，记录错误日志并返回默认的完成结果
     * </p>
     *
     * @param reflectionJson 模型返回的JSON字符串
     * @return 解析后的ReflectionResult对象，如果解析失败则返回默认完成结果
     */
    private ReflectionResult parseReflectionResult(String reflectionJson) {
        try {
            String json = extractJson(reflectionJson);
            return objectMapper.readValue(json, ReflectionResult.class);
        } catch (Exception e) {
            log.error("Failed to parse reflection result: {}", reflectionJson, e);
            ReflectionResult result = new ReflectionResult();
            result.setComplete(true);
            result.setSummary("Failed to parse reflection, assuming completion");
            return result;
        }
    }

    /**
     * 从文本中提取 JSON
     * <p>
     * 从模型返回的文本中提取JSON块：
     * 1. 查找第一个 '{' 和最后一个 '}' 的位置
     * 2. 如果找到有效的JSON块，提取并返回
     * 3. 如果未找到，返回原始文本或空JSON对象
     * </p>
     *
     * @param text 模型返回的文本
     * @return 提取的JSON字符串
     */
    private String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        // 尝试找到 JSON 块
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 解析模型ID
     * <p>
     * 从RunStartCommand的runtimeSnapshotJson中解析模型ID：
     * 1. 解析runtimeSnapshotJson为JSON对象
     * 2. 查找modelPolicy.modelId字段
     * 3. 如果找到，返回模型ID
     * 4. 如果未找到或解析失败，返回默认模型ID（1L）
     * </p>
     *
     * @param command 运行启动命令，包含runtimeSnapshotJson
     * @return 解析出的模型ID，如果未找到则返回默认值1L
     */
    private Long resolveModelId(RunStartCommand command) {
        // 从 runtimeSnapshotJson 中解析模型ID
        try {
            if (StringUtils.hasText(command.getRuntimeSnapshotJson())) {
                JsonNode root = objectMapper.readTree(command.getRuntimeSnapshotJson());
                JsonNode modelPolicy = root.get("modelPolicy");
                if (modelPolicy != null && modelPolicy.hasNonNull("modelId")) {
                    return modelPolicy.get("modelId").asLong();
                }
            }
        } catch (Exception ignored) {
        }
        // 默认返回 1（MVP 默认模型）
        return 1L;
    }

    /**
     * 记录事件
     * <p>
     * 创建RuntimeEvent并添加到事件列表和缓存中：
     * 1. 使用Builder模式构建RuntimeEvent，包含租户ID、用户ID、Agent ID、任务ID、运行ID、跟踪ID、事件类型、事件摘要、payload、发生时间
     * 2. 将事件添加到传入的events列表
     * 3. 将事件添加到eventCache中，以runId为key
     * </p>
     *
     * @param command 运行启动命令，包含租户ID、用户ID、Agent ID等信息
     * @param events 事件列表，用于存储当前执行过程的事件
     * @param eventType 事件类型（如LOOP_STARTED、PLAN_GENERATED等）
     * @param summary 事件摘要
     * @param payloadJson 事件payload的JSON字符串
     */
    private void recordEvent(RunStartCommand command, List<RuntimeEvent> events,
                             String eventType, String summary, String payloadJson) {
        RuntimeEvent event = RuntimeEvent.builder()
                .tenantId(command.getTenantId())
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .traceId(command.getTraceId())
                .eventType(eventType)
                .eventSummary(summary)
                .payloadJson(payloadJson)
                .occurredAt(OffsetDateTime.now())
                .build();
        events.add(event);
        eventCache.computeIfAbsent(command.getRunId(), k -> new ArrayList<>()).add(event);
    }

    /**
     * 构建循环启动事件的payload
     *
     * @param context 循环上下文，包含输入文本和记忆信息
     * @return payload的JSON字符串，包含inputText和memoryCount
     */
    private String buildLoopStartedPayload(AgentLoopContext context) {
        return "{\"inputText\":\"" + safeJson(context.getInputText()) + "\""
                + ",\"memoryCount\":" + (context.getMemories() != null ? context.getMemories().size() : 0) + "}";
    }

    /**
     * 构建执行计划事件的payload
     *
     * @param plan 执行计划对象
     * @return payload的JSON字符串，如果序列化失败则返回包含goal的简化JSON
     */
    private String buildPlanPayload(ExecutionPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            return "{\"goal\":\"" + safeJson(plan.getGoal()) + "\"}";
        }
    }

    /**
     * 获取当前 Agent 版本可用的工具描述列表
     * <p>
     * 从AgentVersion的toolScopeJson中解析可用工具，并构建工具描述列表：
     * 1. 获取AgentVersion对象
     * 2. 解析toolScopeJson获取工具ID集合
     * 3. 查询ToolConfig获取工具详情
     * 4. 构建ToolDescription列表，包含工具ID、代码、名称、类型、风险等级、描述、参数Schema
     * </p>
     *
     * @param context 循环上下文，包含agentVersionId
     * @return 可用工具描述列表，如果获取失败则返回空列表
     */
    private List<ToolDescription> getAvailableToolDescriptions(AgentLoopContext context) {
        if (toolConfigService == null || agentVersionService == null || context.getAgentVersionId() == null) {
            return List.of();
        }

        try {
            // 获取 Agent 版本的工具范围
            AgentVersion version = agentVersionService.getVersion(context.getAgentVersionId());
            if (version == null) {
                return List.of();
            }

            String toolScopeJson = version.getToolScopeJson();
            Set<Long> toolIds = parseToolScopeIds(toolScopeJson);

            if (toolIds.isEmpty()) {
                return List.of();
            }

            // 查询工具详情
            List<ToolConfig> tools = toolConfigService.list(
                    new LambdaQueryWrapper<ToolConfig>()
                            .in(ToolConfig::getId, toolIds)
                            .eq(ToolConfig::getTenantId, context.getTenantId())
                            .eq(ToolConfig::getStatus, "active")
            );

            return tools.stream()
                    .map(tool -> ToolDescription.builder()
                            .toolId(tool.getId())
                            .toolCode(tool.getToolCode())
                            .toolName(tool.getToolName())
                            .toolType(tool.getToolType())
                            .riskLevel(tool.getRiskLevel())
                            .description(tool.getToolName()) // 暂时用 toolName 作为描述
                            .parametersSchema(tool.getSchemaJson())
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to get available tool descriptions", e);
            return List.of();
        }
    }

    /**
     * 解析工具范围 JSON，提取工具ID集合
     * <p>
     * 从toolScopeJson中解析工具ID列表：
     * 1. 检查toolScopeJson是否为空
     * 2. 解析JSON数组
     * 3. 提取每个元素的toolId字段
     * 4. 返回工具ID集合
     * </p>
     *
     * @param toolScopeJson 工具范围JSON字符串，格式如：[{"toolId": 1}, {"toolId": 2}]
     * @return 工具ID集合，如果解析失败则返回空集合
     */
    private Set<Long> parseToolScopeIds(String toolScopeJson) {
        if (!StringUtils.hasText(toolScopeJson)) {
            return Set.of();
        }
        try {
            JsonNode scope = objectMapper.readTree(toolScopeJson);
            if (!scope.isArray()) {
                return Set.of();
            }
            Set<Long> toolIds = ConcurrentHashMap.newKeySet();
            for (JsonNode item : scope) {
                if (item.hasNonNull("toolId")) {
                    toolIds.add(item.get("toolId").asLong());
                }
            }
            return toolIds;
        } catch (Exception e) {
            log.error("Failed to parse tool scope JSON: {}", toolScopeJson, e);
            return Set.of();
        }
    }

    /**
     * 记录知识来源
     * <p>
     * 将模型识别的知识来源记录到事件中：
     * 1. 检查knowledgeSources是否为空
     * 2. 构建payload JSON，包含所有知识来源的sourceTitle、confidence、quoted
     * 3. 记录KNOWLEDGE_SOURCES事件
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表
     * @param knowledgeSources 知识来源列表
     */
    private void recordKnowledgeSources(RunStartCommand command, List<RuntimeEvent> events,
                                        List<KnowledgeSource> knowledgeSources) {
        if (knowledgeSources == null || knowledgeSources.isEmpty()) {
            return;
        }

        StringBuilder payload = new StringBuilder();
        payload.append("{\"sources\":[");
        for (int i = 0; i < knowledgeSources.size(); i++) {
            KnowledgeSource source = knowledgeSources.get(i);
            if (i > 0) {
                payload.append(",");
            }
            payload.append("{");
            payload.append("\"sourceTitle\":\"").append(safeJson(source.getSourceTitle())).append("\"");
            payload.append(",\"confidence\":\"").append(safeJson(source.getConfidence())).append("\"");
            payload.append(",\"quoted\":\"").append(safeJson(source.getQuoted())).append("\"");
            payload.append("}");
        }
        payload.append("]}");

        recordEvent(command, events, "KNOWLEDGE_SOURCES", "Knowledge sources cited", payload.toString());
    }

    /**
     * 保存抽取的记忆
     * <p>
     * 将模型从对话中抽取的记忆保存到数据库：
     * 1. 检查agentMemoryService和extractedMemories是否为空
     * 2. 遍历extractedMemories列表
     * 3. 为每个记忆创建CreateAgentMemoryCommand
     * 4. 设置租户ID、Agent ID、任务ID、用户ID、记忆类型、范围、内容、置信度
     * 5. 调用agentMemoryService.createConfirmedMemory保存记忆
     * 6. 记录MEMORY_EXTRACTED事件
     * 7. 如果保存成功，记录MEMORIES_SAVED事件
     * </p>
     *
     * @param command 运行启动命令
     * @param events 事件列表
     * @param extractedMemories 抽取的记忆列表
     */
    private void saveExtractedMemories(RunStartCommand command, List<RuntimeEvent> events,
                                       List<ExtractedMemory> extractedMemories) {
        if (agentMemoryService == null || extractedMemories == null || extractedMemories.isEmpty()) {
            return;
        }

        int savedCount = 0;
        for (ExtractedMemory extracted : extractedMemories) {
            try {
                // 创建记忆实体
                com.xiaoai.agent.memory.model.CreateAgentMemoryCommand createCommand =
                        new com.xiaoai.agent.memory.model.CreateAgentMemoryCommand();
                createCommand.setTenantId(command.getTenantId());
                createCommand.setAgentId(command.getAgentId());
                createCommand.setTaskId(command.getTaskId());
                createCommand.setUserId(command.getUserId());
                createCommand.setMemoryType(extracted.getMemoryType() != null ? extracted.getMemoryType() : "fact");
                createCommand.setMemoryScope(extracted.getScope() != null ? extracted.getScope() : "task");
                createCommand.setSummaryText(extracted.getContent());
                createCommand.setConfidence(extracted.getConfidence() != null ? extracted.getConfidence() : "medium");

                // 保存记忆
                agentMemoryService.createConfirmedMemory(createCommand);
                savedCount++;

                recordEvent(command, events, "MEMORY_EXTRACTED", "Memory extracted from execution",
                        "{\"memoryType\":\"" + safeJson(extracted.getMemoryType()) + "\""
                                + ",\"scope\":\"" + safeJson(extracted.getScope()) + "\""
                                + ",\"confidence\":\"" + safeJson(extracted.getConfidence()) + "\""
                                + ",\"content\":\"" + safeJson(extracted.getContent()) + "\"}");

            } catch (Exception e) {
                log.error("Failed to save extracted memory: {}", extracted.getContent(), e);
            }
        }

        if (savedCount > 0) {
            recordEvent(command, events, "MEMORIES_SAVED", "Extracted memories saved",
                    "{\"savedCount\":" + savedCount + "}");
        }
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

    /**
     * Agent 循环上下文
     */
    private static class AgentLoopContext {
        private String inputText;
        private Long tenantId;
        private Long userId;
        private Long agentId;
        private Long agentVersionId;
        private List<AgentMemory> memories;
        private List<KnowledgeContext> knowledgeContexts;
        private List<ToolCallContext> toolCallContexts;
        private List<IterationResult> iterationResults = new ArrayList<>();
        private List<Skill> matchedSkills = new ArrayList<>();
        private String skillContextText;

        // Getters and setters
public String getInputText() { return inputText; }
public void setInputText(String inputText) { this.inputText = inputText; }
public Long getTenantId() { return tenantId; }
public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
public Long getUserId() { return userId; }
public void setUserId(Long userId) { this.userId = userId; }
public Long getAgentId() { return agentId; }
public void setAgentId(Long agentId) { this.agentId = agentId; }
public Long getAgentVersionId() { return agentVersionId; }
public void setAgentVersionId(Long agentVersionId) { this.agentVersionId = agentVersionId; }
public List<AgentMemory> getMemories() { return memories; }
public void setMemories(List<AgentMemory> memories) { this.memories = memories; }
public List<KnowledgeContext> getKnowledgeContexts() { return knowledgeContexts; }
public void setKnowledgeContexts(List<KnowledgeContext> knowledgeContexts) { this.knowledgeContexts = knowledgeContexts; }
public List<ToolCallContext> getToolCallContexts() { return toolCallContexts; }
public void setToolCallContexts(List<ToolCallContext> toolCallContexts) { this.toolCallContexts = toolCallContexts; }
public List<IterationResult> getIterationResults() { return iterationResults; }
public void addIterationResults(int iteration, List<StepResult> stepResults, ReflectionResult reflection) {
            iterationResults.add(new IterationResult(iteration, stepResults, reflection));
        }
public List<Skill> getMatchedSkills() { return matchedSkills; }
public void setMatchedSkills(List<Skill> matchedSkills) { this.matchedSkills = matchedSkills; }
public String getSkillContextText() { return skillContextText; }
public void setSkillContextText(String skillContextText) { this.skillContextText = skillContextText; }
    }

    /**
     * 知识上下文
     */
    private record KnowledgeContext(Long documentId, Long chunkId, String chunkText,
                                    String sourceTitle, String confidence) {}

    /**
     * 工具调用上下文
     */
    private record ToolCallContext(Long toolId, String toolCode, String resultJson, String status) {}

    /**
     * 迭代结果
     */
    private record IterationResult(int iteration, List<StepResult> stepResults, ReflectionResult reflection) {}

    /**
     * 步骤结果
     */
    @Getter
    @Setter
    private static class StepResult {
        private String stepId;
        private String stepType;
        private String status; // success, failed, blocked
        private String error;
        private Long toolId;
        private String toolCode;
        private String resultJson;
        private List<KnowledgeRetrieveResult> knowledgeResults;
        private Long approvalRequestId;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    /**
     * 反思结果
     */
    @Getter
    @Setter
    private static class ReflectionResult {
        private boolean complete;
        private String summary;
        private List<KnowledgeSource> knowledgeSources;
        private String lowConfidenceWarning;
        private boolean needsAdjustment;
        private String adjustmentReason;
        private List<ExtractedMemory> extractedMemories;
    }

    /**
     * 知识来源
     */
    @Getter
    @Setter
    private static class KnowledgeSource {
        private String sourceTitle;
        private String confidence;
        private String quoted;
    }

    /**
     * 抽取的记忆
     */
    @Getter
    @Setter
    private static class ExtractedMemory {
        private String memoryType; // decision, preference, constraint, fact
        private String content;
        private String confidence; // high, medium, low
        private String scope; // task, session, agent
    }
}
