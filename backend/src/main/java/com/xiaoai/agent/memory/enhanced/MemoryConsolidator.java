package com.xiaoai.agent.memory.enhanced;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆整合器
 * 负责记忆的去重、合并和整合
 *
 * 这是 Hermes Agent 闭环学习的核心组件
 */
@Component
public class MemoryConsolidator {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidator.class);

    private final AgentMemoryService memoryService;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    // 相似度阈值（0-100）
    private static final int SIMILARITY_THRESHOLD = 70;

    @Autowired
    public MemoryConsolidator(AgentMemoryService memoryService,
                             ModelGateway modelGateway,
                             ObjectMapper objectMapper) {
        this.memoryService = memoryService;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 整合记忆
     * 对指定租户/Agent/用户的记忆进行去重和合并
     *
     * @param tenantId 租户ID
     * @param agentId Agent ID
     * @param userId 用户ID
     * @return 整合后的记忆数量
     */
    public int consolidateMemories(Long tenantId, Long agentId, Long userId) {
        log.info("Starting memory consolidation: tenantId={}, agentId={}, userId={}",
                tenantId, agentId, userId);

        // 1. 获取所有活跃记忆
        List<AgentMemory> memories = memoryService.listConfirmedMemoriesForRuntime(
                tenantId, agentId, null, null, userId, List.of("task", "session", "agent"), 1000);

        if (memories.size() < 2) {
            log.info("Not enough memories to consolidate: count={}", memories.size());
            return 0;
        }

        // 2. 分组相似记忆
        List<List<AgentMemory>> groups = groupSimilarMemories(memories);

        int consolidatedCount = 0;

        // 3. 对每组进行整合
        for (List<AgentMemory> group : groups) {
            if (group.size() > 1) {
                consolidateGroup(group);
                consolidatedCount += group.size() - 1; // 合并后减少的数量
            }
        }

        log.info("Memory consolidation completed: totalGroups={}, consolidatedCount={}",
                groups.size(), consolidatedCount);

        return consolidatedCount;
    }

    /**
     * 将相似记忆分组
     */
    private List<List<AgentMemory>> groupSimilarMemories(List<AgentMemory> memories) {
        List<List<AgentMemory>> groups = new ArrayList<>();
        List<AgentMemory> remaining = new ArrayList<>(memories);

        while (!remaining.isEmpty()) {
            AgentMemory current = remaining.remove(0);
            List<AgentMemory> group = new ArrayList<>();
            group.add(current);

            // 找到所有相似的记忆
            List<AgentMemory> toRemove = new ArrayList<>();
            for (AgentMemory other : remaining) {
                if (areSimilar(current, other)) {
                    group.add(other);
                    toRemove.add(other);
                }
            }
            remaining.removeAll(toRemove);

            groups.add(group);
        }

        return groups;
    }

    /**
     * 判断两个记忆是否相似
     * 使用简单的文本相似度计算（可以后续用向量相似度改进）
     */
    private boolean areSimilar(AgentMemory memory1, AgentMemory memory2) {
        // 必须同类型、同范围
        if (!memory1.getMemoryType().equals(memory2.getMemoryType()) ||
            !memory1.getMemoryScope().equals(memory2.getMemoryScope())) {
            return false;
        }

        // 计算文本相似度
        String text1 = memory1.getSummaryText();
        String text2 = memory2.getSummaryText();

        int similarity = calculateSimilarity(text1, text2);

        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 计算文本相似度（简单的 Jaccard 相似度）
     */
    private int calculateSimilarity(String text1, String text2) {
        if (!StringUtils.hasText(text1) || !StringUtils.hasText(text2)) {
            return 0;
        }

        // 分词
        String[] words1 = text1.toLowerCase().split("\\s+");
        String[] words2 = text2.toLowerCase().split("\\s+");

        // 计算 Jaccard 相似度
        long intersection = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equals(word2)) {
                    intersection++;
                    break;
                }
            }
        }

        long union = words1.length + words2.length - intersection;

        if (union == 0) {
            return 0;
        }

        return (int) ((intersection * 100.0) / union);
    }

    /**
     * 整合一组相似记忆
     */
    private void consolidateGroup(List<AgentMemory> group) {
        if (group.size() < 2) {
            return;
        }

        log.info("Consolidating memory group: size={}, type={}, scope={}",
                group.size(), group.get(0).getMemoryType(), group.get(0).getMemoryScope());

        try {
            // 1. 调用模型生成整合后的记忆
            String consolidatedText = generateConsolidatedMemory(group);

            if (!StringUtils.hasText(consolidatedText)) {
                log.warn("Failed to generate consolidated memory");
                return;
            }

            // 2. 更新第一条记忆
            AgentMemory primary = group.get(0);
            primary.setSummaryText(consolidatedText);
            primary.setConfidence("high"); // 整合后的记忆置信度更高
            memoryService.updateById(primary);

            // 3. 归档其他记忆
            for (int i = 1; i < group.size(); i++) {
                AgentMemory memory = group.get(i);
                memoryService.archiveMemory(memory.getId());
            }

            log.info("Memory group consolidated: primaryId={}, archivedCount={}",
                    primary.getId(), group.size() - 1);

        } catch (Exception e) {
            log.error("Failed to consolidate memory group", e);
        }
    }

    /**
     * 调用模型生成整合后的记忆
     */
    private String generateConsolidatedMemory(List<AgentMemory> group) {
        try {
            // 构建提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("请将以下相似的记忆整合成一条更完整、更清晰的记忆：\n\n");

            for (int i = 0; i < group.size(); i++) {
                AgentMemory memory = group.get(i);
                prompt.append(String.format("%d. %s\n", i + 1, memory.getSummaryText()));
            }

            prompt.append("\n要求：\n");
            prompt.append("- 保留所有重要信息\n");
            prompt.append("- 去除重复内容\n");
            prompt.append("- 使表述更清晰\n");
            prompt.append("- 保持原始类型和范围\n");
            prompt.append("\n只返回整合后的记忆文本，不要其他内容。");

            // 调用模型
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L); // 使用默认模型
            command.setPrompt(prompt.toString());

            ChatModelResponse response = modelGateway.chat(command);
            return response.getContent().trim();

        } catch (Exception e) {
            log.error("Failed to generate consolidated memory", e);
            return null;
        }
    }

    /**
     * 主动记忆提醒
     * 判断当前对话是否值得保存记忆
     *
     * @param tenantId 租户ID
     * @param agentId Agent ID
     * @param userId 用户ID
     * @param conversationText 对话文本
     * @return 是否应该保存记忆
     */
    public boolean shouldSaveMemory(Long tenantId, Long agentId, Long userId, String conversationText) {
        if (!StringUtils.hasText(conversationText)) {
            return false;
        }

        try {
            // 构建提示词
            String prompt = String.format("""
                    请判断以下对话是否包含值得长期保存的重要信息。

                    对话内容：
                    %s

                    判断标准：
                    - 包含用户偏好或习惯
                    - 包含重要决策或结论
                    - 包含项目约束或规则
                    - 包含关键事实信息
                    - 对未来任务有指导意义

                    如果值得保存，返回 "yes"，否则返回 "no"。
                    只返回 yes 或 no，不要其他内容。
                    """, conversationText);

            // 调用模型
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L);
            command.setPrompt(prompt);

            ChatModelResponse response = modelGateway.chat(command);
            String result = response.getContent().trim().toLowerCase();

            return result.contains("yes");

        } catch (Exception e) {
            log.error("Failed to check if should save memory", e);
            return false;
        }
    }
}
