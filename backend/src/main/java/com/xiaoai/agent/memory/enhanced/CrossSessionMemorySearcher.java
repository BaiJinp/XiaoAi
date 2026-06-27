package com.xiaoai.agent.memory.enhanced;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.mapper.AgentMemoryMapper;
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
 * 跨会话记忆搜索器
 * 支持全文搜索和 LLM 摘要
 *
 * 这是 Hermes Agent 闭环学习的关键组件
 */
@Component
public class CrossSessionMemorySearcher {

    private static final Logger log = LoggerFactory.getLogger(CrossSessionMemorySearcher.class);

    private final AgentMemoryMapper memoryMapper;
    private final ModelGateway modelGateway;

    @Autowired
    public CrossSessionMemorySearcher(AgentMemoryMapper memoryMapper,
                                     ModelGateway modelGateway) {
        this.memoryMapper = memoryMapper;
        this.modelGateway = modelGateway;
    }

    /**
     * 搜索记忆
     *
     * @param tenantId 租户ID
     * @param agentId Agent ID
     * @param userId 用户ID
     * @param query 搜索查询
     * @param limit 返回数量限制
     * @return 匹配的记忆列表
     */
    public List<AgentMemory> search(Long tenantId, Long agentId, Long userId,
                                     String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        log.info("Searching memories: tenantId={}, agentId={}, userId={}, query={}",
                tenantId, agentId, userId, query);

        try {
            // 1. 全文搜索（使用 LIKE 模拟，后续可以改用 FTS5）
            List<AgentMemory> candidates = fullTextSearch(tenantId, agentId, userId, query);

            if (candidates.isEmpty()) {
                log.info("No memories found for query: {}", query);
                return List.of();
            }

            // 2. 使用 LLM 对搜索结果进行排序和摘要
            List<AgentMemory> ranked = rankAndSummarize(candidates, query, limit);

            log.info("Memory search completed: query={}, candidates={}, returned={}",
                    query, candidates.size(), ranked.size());

            return ranked;

        } catch (Exception e) {
            log.error("Failed to search memories", e);
            return List.of();
        }
    }

    /**
     * 全文搜索
     */
    private List<AgentMemory> fullTextSearch(Long tenantId, Long agentId, Long userId, String query) {
        String pattern = "%" + query + "%";

        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getStatus, "confirmed")
                .and(w -> w
                        .like(AgentMemory::getSummaryText, query)
                        .or().like(AgentMemory::getSourceText, query)
                );

        if (agentId != null) {
            wrapper.eq(AgentMemory::getAgentId, agentId);
        }

        if (userId != null) {
            wrapper.eq(AgentMemory::getUserId, userId);
        }

        wrapper.orderByDesc(AgentMemory::getCreatedAt)
               .last("limit 100"); // 先取 100 条候选

        return memoryMapper.selectList(wrapper);
    }

    /**
     * 使用 LLM 对搜索结果进行排序和摘要
     */
    private List<AgentMemory> rankAndSummarize(List<AgentMemory> candidates, String query, int limit) {
        if (candidates.size() <= limit) {
            return candidates;
        }

        try {
            // 构建提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("请根据以下查询，从候选记忆中选择最相关的 ").append(limit).append(" 条：\n\n");
            prompt.append("查询：").append(query).append("\n\n");
            prompt.append("候选记忆：\n");

            for (int i = 0; i < candidates.size(); i++) {
                AgentMemory memory = candidates.get(i);
                prompt.append(String.format("%d. [ID=%d] %s\n",
                        i + 1, memory.getId(), memory.getSummaryText()));
            }

            prompt.append("\n请返回最相关的记忆ID列表（用逗号分隔），按相关性从高到低排序：");

            // 调用模型
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L);
            command.setPrompt(prompt.toString());

            ChatModelResponse response = modelGateway.chat(command);
            String result = response.getContent().trim();

            // 解析结果
            List<Long> rankedIds = parseRankedIds(result);

            // 按排序结果返回
            List<AgentMemory> ranked = new ArrayList<>();
            for (Long id : rankedIds) {
                candidates.stream()
                        .filter(m -> m.getId().equals(id))
                        .findFirst()
                        .ifPresent(ranked::add);
            }

            return ranked.stream().limit(limit).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to rank and summarize memories", e);
            return candidates.stream().limit(limit).collect(Collectors.toList());
        }
    }

    /**
     * 解析排序后的ID列表
     */
    private List<Long> parseRankedIds(String result) {
        List<Long> ids = new ArrayList<>();

        try {
            // 提取数字
            String[] parts = result.split("[,\\s]+");
            for (String part : parts) {
                part = part.replaceAll("[^0-9]", "");
                if (!part.isEmpty()) {
                    ids.add(Long.parseLong(part));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse ranked IDs: {}", result, e);
        }

        return ids;
    }

    /**
     * 搜索并生成摘要
     *
     * @param tenantId 租户ID
     * @param agentId Agent ID
     * @param userId 用户ID
     * @param query 搜索查询
     * @return 搜索结果的摘要
     */
    public String searchAndSummarize(Long tenantId, Long agentId, Long userId, String query) {
        List<AgentMemory> memories = search(tenantId, agentId, userId, query, 10);

        if (memories.isEmpty()) {
            return "没有找到相关记忆。";
        }

        try {
            // 构建提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("请根据以下记忆，生成一个简洁的摘要来回答查询：\n\n");
            prompt.append("查询：").append(query).append("\n\n");
            prompt.append("相关记忆：\n");

            for (int i = 0; i < memories.size(); i++) {
                AgentMemory memory = memories.get(i);
                prompt.append(String.format("%d. %s\n", i + 1, memory.getSummaryText()));
            }

            prompt.append("\n请生成一个简洁、准确的摘要：");

            // 调用模型
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L);
            command.setPrompt(prompt.toString());

            ChatModelResponse response = modelGateway.chat(command);
            return response.getContent().trim();

        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            return "找到了 " + memories.size() + " 条相关记忆，但无法生成摘要。";
        }
    }
}
