package com.xiaoai.agent.memory.enhanced;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.mapper.AgentMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆回顾调度器
 * 定期执行记忆整合和清理
 *
 * 这是 Hermes Agent 闭环学习的自动化组件
 */
@Component
public class MemoryReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryReviewScheduler.class);

    private final AgentMemoryMapper memoryMapper;
    private final MemoryConsolidator consolidator;

    @Autowired
    public MemoryReviewScheduler(AgentMemoryMapper memoryMapper,
                                 MemoryConsolidator consolidator) {
        this.memoryMapper = memoryMapper;
        this.consolidator = consolidator;
    }

    /**
     * 每天凌晨 2 点执行记忆整合
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMemoryConsolidation() {
        log.info("Starting daily memory consolidation");

        try {
            // 获取所有活跃租户
            List<Long> tenantIds = getActiveTenantIds();

            int totalConsolidated = 0;

            for (Long tenantId : tenantIds) {
                try {
                    // 获取租户下的所有 Agent
                    List<Long> agentIds = getAgentIdsForTenant(tenantId);

                    for (Long agentId : agentIds) {
                        // 获取 Agent 下的所有用户
                        List<Long> userIds = getUserIdsForAgent(tenantId, agentId);

                        for (Long userId : userIds) {
                            int consolidated = consolidator.consolidateMemories(tenantId, agentId, userId);
                            totalConsolidated += consolidated;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to consolidate memories for tenant: {}", tenantId, e);
                }
            }

            log.info("Daily memory consolidation completed: totalConsolidated={}", totalConsolidated);

        } catch (Exception e) {
            log.error("Daily memory consolidation failed", e);
        }
    }

    /**
     * 每周日凌晨 3 点执行深度记忆清理
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void weeklyMemoryCleanup() {
        log.info("Starting weekly memory cleanup");

        try {
            // 清理过期的临时记忆
            int cleaned = cleanupExpiredMemories();

            // 归档长期未使用的记忆
            int archived = archiveUnusedMemories();

            log.info("Weekly memory cleanup completed: cleaned={}, archived={}", cleaned, archived);

        } catch (Exception e) {
            log.error("Weekly memory cleanup failed", e);
        }
    }

    /**
     * 清理过期的临时记忆
     */
    private int cleanupExpiredMemories() {
        // 清理 30 天前的临时记忆（scope = task）
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);

        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getMemoryScope, "task")
                .eq(AgentMemory::getStatus, "confirmed")
                .lt(AgentMemory::getCreatedAt, thirtyDaysAgo);

        List<AgentMemory> expired = memoryMapper.selectList(wrapper);

        int count = 0;
        for (AgentMemory memory : expired) {
            memory.setStatus("archived");
            memory.setUpdatedAt(OffsetDateTime.now());
            memoryMapper.updateById(memory);
            count++;
        }

        return count;
    }

    /**
     * 归档长期未使用的记忆
     */
    private int archiveUnusedMemories() {
        // 归档 90 天未使用的记忆
        OffsetDateTime ninetyDaysAgo = OffsetDateTime.now().minusDays(90);

        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getStatus, "confirmed")
                .lt(AgentMemory::getUpdatedAt, ninetyDaysAgo);

        List<AgentMemory> unused = memoryMapper.selectList(wrapper);

        int count = 0;
        for (AgentMemory memory : unused) {
            memory.setStatus("archived");
            memory.setUpdatedAt(OffsetDateTime.now());
            memoryMapper.updateById(memory);
            count++;
        }

        return count;
    }

    /**
     * 获取活跃租户列表
     */
    private List<Long> getActiveTenantIds() {
        // TODO: 从租户服务获取活跃租户
        // 这里简化处理，返回固定值
        return List.of(100L);
    }

    /**
     * 获取租户下的 Agent 列表
     */
    private List<Long> getAgentIdsForTenant(Long tenantId) {
        // TODO: 从 Agent 服务获取
        return List.of();
    }

    /**
     * 获取 Agent 下的用户列表
     */
    private List<Long> getUserIdsForAgent(Long tenantId, Long agentId) {
        // 从记忆表中获取有记忆的用户
        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getAgentId, agentId)
                .eq(AgentMemory::getStatus, "confirmed")
                .isNotNull(AgentMemory::getUserId)
                .select(AgentMemory::getUserId)
                .groupBy(AgentMemory::getUserId);

        List<AgentMemory> memories = memoryMapper.selectList(wrapper);

        return memories.stream()
                .map(AgentMemory::getUserId)
                .distinct()
                .toList();
    }

    /**
     * 手动触发记忆整合（用于测试）
     */
    public void triggerConsolidation(Long tenantId, Long agentId, Long userId) {
        log.info("Manual memory consolidation triggered: tenantId={}, agentId={}, userId={}",
                tenantId, agentId, userId);

        int consolidated = consolidator.consolidateMemories(tenantId, agentId, userId);

        log.info("Manual consolidation completed: consolidated={}", consolidated);
    }

    /**
     * 获取记忆统计信息
     */
    public Map<String, Object> getMemoryStats(Long tenantId, Long agentId, Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 总记忆数
        LambdaQueryWrapper<AgentMemory> totalWrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getStatus, "confirmed");

        if (agentId != null) {
            totalWrapper.eq(AgentMemory::getAgentId, agentId);
        }
        if (userId != null) {
            totalWrapper.eq(AgentMemory::getUserId, userId);
        }

        long total = memoryMapper.selectCount(totalWrapper);
        stats.put("totalMemories", total);

        // 按类型统计
        for (String type : List.of("decision", "preference", "constraint", "fact")) {
            LambdaQueryWrapper<AgentMemory> typeWrapper = new LambdaQueryWrapper<AgentMemory>()
                    .eq(AgentMemory::getTenantId, tenantId)
                    .eq(AgentMemory::getMemoryType, type)
                    .eq(AgentMemory::getStatus, "confirmed");

            if (agentId != null) {
                typeWrapper.eq(AgentMemory::getAgentId, agentId);
            }
            if (userId != null) {
                typeWrapper.eq(AgentMemory::getUserId, userId);
            }

            long count = memoryMapper.selectCount(typeWrapper);
            stats.put(type + "Memories", count);
        }

        // 按范围统计
        for (String scope : List.of("task", "session", "agent")) {
            LambdaQueryWrapper<AgentMemory> scopeWrapper = new LambdaQueryWrapper<AgentMemory>()
                    .eq(AgentMemory::getTenantId, tenantId)
                    .eq(AgentMemory::getMemoryScope, scope)
                    .eq(AgentMemory::getStatus, "confirmed");

            if (agentId != null) {
                scopeWrapper.eq(AgentMemory::getAgentId, agentId);
            }
            if (userId != null) {
                scopeWrapper.eq(AgentMemory::getUserId, userId);
            }

            long count = memoryMapper.selectCount(scopeWrapper);
            stats.put(scope + "Memories", count);
        }

        return stats;
    }
}
