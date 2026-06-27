package com.xiaoai.agent.runtime.engine;

import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 分层上下文
 * 系统 prompt + 用户历史 + 任务上下文
 */
@Getter
@Builder
public class LayeredContext {

    /**
     * 系统 prompt（Agent 角色、职责、边界）
     */
    private final String systemPrompt;

    /**
     * Agent 角色定义
     */
    private final String agentRole;

    /**
     * Agent 职责描述
     */
    private final String agentResponsibility;

    /**
     * Agent 边界约束
     */
    private final String agentBoundary;

    /**
     * 用户历史对话（最近 N 轮）
     */
    private final List<ConversationTurn> conversationHistory;

    /**
     * 当前任务上下文
     */
    private final TaskContext taskContext;

    /**
     * 已确认记忆
     */
    private final List<AgentMemory> memories;

    /**
     * 模型上下文窗口大小（token 数）
     */
    private final int contextWindow;

    /**
     * 对话轮次
     */
    @Getter
    @Builder
    public static class ConversationTurn {
        private final String role; // user, assistant
        private final String content;
        private final long timestamp;
    }

    /**
     * 任务上下文
     */
    @Getter
    @Builder
    public static class TaskContext {
        private final Long taskId;
        private final Long runId;
        private final String inputText;
        private final String channelType;
        private final List<KnowledgeContext> knowledgeContexts;
        private final List<ToolCallContext> toolCallContexts;
        private final List<IterationResult> iterationResults;
    }

    /**
     * 知识上下文
     */
    @Getter
    @Builder
    public static class KnowledgeContext {
        private final Long documentId;
        private final Long chunkId;
        private final String chunkText;
        private final String sourceTitle;
        private final String confidence;
    }

    /**
     * 工具调用上下文
     */
    @Getter
    @Builder
    public static class ToolCallContext {
        private final Long toolId;
        private final String toolCode;
        private final String resultJson;
        private final String status;
    }

    /**
     * 迭代结果
     */
    @Getter
    @Builder
    public static class IterationResult {
        private final int iteration;
        private final List<StepResult> stepResults;
        private final ReflectionResult reflection;
    }

    /**
     * 步骤结果
     */
    @Getter
    @Builder
    public static class StepResult {
        private final String stepId;
        private final String stepType;
        private final String status;
        private final String error;
        private final String resultJson;
    }

    /**
     * 反思结果
     */
    @Getter
    @Builder
    public static class ReflectionResult {
        private final boolean complete;
        private final String summary;
        private final boolean needsAdjustment;
        private final String adjustmentReason;
    }
}
