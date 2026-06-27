package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文构建器
 * 负责构建分层上下文，包括系统 prompt、用户历史、任务上下文
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);
    private static final int DEFAULT_CONTEXT_WINDOW = 8192;
    private static final int MAX_HISTORY_TURNS = 10;

    private final AgentVersionService agentVersionService;
    private final AgentMemoryService agentMemoryService;
    private final ObjectMapper objectMapper;

    public ContextBuilder(AgentVersionService agentVersionService,
                          AgentMemoryService agentMemoryService,
                          ObjectMapper objectMapper) {
        this.agentVersionService = agentVersionService;
        this.agentMemoryService = agentMemoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建分层上下文
     */
    public LayeredContext build(RunStartCommand command,
                                 List<LayeredContext.KnowledgeContext> knowledgeContexts,
                                 List<LayeredContext.ToolCallContext> toolCallContexts,
                                 List<LayeredContext.IterationResult> iterationResults) {
        // 1. 构建系统 prompt
        String systemPrompt = buildSystemPrompt(command);

        // 2. 构建 Agent 信息
        AgentInfo agentInfo = getAgentInfo(command);

        // 3. 加载记忆
        List<AgentMemory> memories = loadMemories(command);

        // 4. 构建任务上下文
        LayeredContext.TaskContext taskContext = LayeredContext.TaskContext.builder()
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .inputText(command.getInputText())
                .channelType(command.getChannelType())
                .knowledgeContexts(knowledgeContexts != null ? knowledgeContexts : List.of())
                .toolCallContexts(toolCallContexts != null ? toolCallContexts : List.of())
                .iterationResults(iterationResults != null ? iterationResults : List.of())
                .build();

        // 5. 获取上下文窗口大小
        int contextWindow = getContextWindow(command);

        return LayeredContext.builder()
                .systemPrompt(systemPrompt)
                .agentRole(agentInfo.role)
                .agentResponsibility(agentInfo.responsibility)
                .agentBoundary(agentInfo.boundary)
                .conversationHistory(new ArrayList<>()) // TODO: 从数据库加载历史对话
                .taskContext(taskContext)
                .memories(memories)
                .contextWindow(contextWindow)
                .build();
    }

    /**
     * 构建系统 prompt
     */
    private String buildSystemPrompt(RunStartCommand command) {
        AgentInfo agentInfo = getAgentInfo(command);

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个智能助手。\n\n");

        if (StringUtils.hasText(agentInfo.role)) {
            systemPrompt.append("角色：").append(agentInfo.role).append("\n");
        }

        if (StringUtils.hasText(agentInfo.responsibility)) {
            systemPrompt.append("职责：").append(agentInfo.responsibility).append("\n");
        }

        if (StringUtils.hasText(agentInfo.boundary)) {
            systemPrompt.append("边界：").append(agentInfo.boundary).append("\n");
        }

        return systemPrompt.toString();
    }

    /**
     * 获取 Agent 信息
     */
    private AgentInfo getAgentInfo(RunStartCommand command) {
        if (agentVersionService == null || command.getAgentVersionId() == null) {
            return new AgentInfo(null, null, null);
        }

        try {
            AgentVersion version = agentVersionService.getVersion(command.getAgentVersionId());
            if (version == null) {
                return new AgentInfo(null, null, null);
            }

            return new AgentInfo(
                    version.getRolePrompt(),
                    version.getResponsibilityText(),
                    version.getBoundaryText()
            );
        } catch (Exception e) {
            log.error("Failed to get agent info", e);
            return new AgentInfo(null, null, null);
        }
    }

    /**
     * 加载记忆
     */
    private List<AgentMemory> loadMemories(RunStartCommand command) {
        if (agentMemoryService == null) {
            return List.of();
        }

        try {
            return agentMemoryService.listConfirmedMemoriesForRuntime(
                    command.getTenantId(),
                    command.getAgentId(),
                    command.getTaskId(),
                    null, // sessionId
                    command.getUserId(),
                    List.of("task"),
                    5
            );
        } catch (Exception e) {
            log.error("Failed to load memories", e);
            return List.of();
        }
    }

    /**
     * 获取上下文窗口大小
     */
    private int getContextWindow(RunStartCommand command) {
        // TODO: 从模型配置中获取
        return DEFAULT_CONTEXT_WINDOW;
    }

    /**
     * 估算 token 数量
     */
    public int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        // 粗略估算：1 token ≈ 4 个字符
        return text.length() / 4;
    }

    /**
     * 裁剪对话历史，避免超过上下文窗口
     */
    public List<LayeredContext.ConversationTurn> trimHistory(
            List<LayeredContext.ConversationTurn> history,
            int availableTokens) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<LayeredContext.ConversationTurn> trimmed = new ArrayList<>();
        int usedTokens = 0;

        // 从最新的对话开始，向前添加
        for (int i = history.size() - 1; i >= 0 && trimmed.size() < MAX_HISTORY_TURNS; i--) {
            LayeredContext.ConversationTurn turn = history.get(i);
            int turnTokens = estimateTokens(turn.getContent());

            if (usedTokens + turnTokens > availableTokens) {
                break;
            }

            trimmed.add(0, turn);
            usedTokens += turnTokens;
        }

        return trimmed;
    }

    /**
     * Agent 信息
     */
    private record AgentInfo(String role, String responsibility, String boundary) {
    }
}
