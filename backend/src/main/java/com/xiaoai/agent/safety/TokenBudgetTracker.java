package com.xiaoai.agent.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 预算跟踪器
 * 跟踪和管理 token 使用情况，提供预算限制和超限保护
 */
@Component
public class TokenBudgetTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetTracker.class);

    /**
     * 默认日预算：100万 tokens
     */
    private static final long DEFAULT_DAILY_BUDGET = 1_000_000;

    /**
     * 默认任务预算：10万 tokens
     */
    private static final long DEFAULT_TASK_BUDGET = 100_000;

    /**
     * 租户级日预算缓存：tenantId -> dailyUsage
     */
    private final Map<Long, DailyUsage> tenantDailyUsage = new ConcurrentHashMap<>();

    /**
     * 任务级预算缓存：taskId -> taskUsage
     */
    private final Map<Long, AtomicLong> taskUsage = new ConcurrentHashMap<>();

    /**
     * 日使用量记录
     */
    private static class DailyUsage {
        private final LocalDate date;
        private final AtomicLong totalTokens = new AtomicLong(0);

        DailyUsage(LocalDate date) {
            this.date = date;
        }

        long addTokens(long tokens) {
            return totalTokens.addAndGet(tokens);
        }

        long getTotalTokens() {
            return totalTokens.get();
        }

        boolean isSameDay() {
            return date.equals(LocalDate.now());
        }
    }

    /**
     * 预算检查结果
     */
    public static class BudgetCheckResult {
        private final boolean allowed;
        private final String reason;
        private final long currentUsage;
        private final long budget;

        public BudgetCheckResult(boolean allowed, String reason, long currentUsage, long budget) {
            this.allowed = allowed;
            this.reason = reason;
            this.currentUsage = currentUsage;
            this.budget = budget;
        }
public boolean isAllowed() {
            return allowed;
        }
public String getReason() {
            return reason;
        }
public long getCurrentUsage() {
            return currentUsage;
        }
public long getBudget() {
            return budget;
        }
    }

    /**
     * 检查租户日预算
     *
     * @param tenantId 租户ID
     * @param estimatedTokens 预估 token 使用量
     * @return 预算检查结果
     */
    public BudgetCheckResult checkTenantDailyBudget(Long tenantId, long estimatedTokens) {
        if (tenantId == null) {
            return new BudgetCheckResult(true, "No tenant ID", 0, DEFAULT_DAILY_BUDGET);
        }

        DailyUsage usage = tenantDailyUsage.compute(tenantId, (id, existing) -> {
            if (existing == null || !existing.isSameDay()) {
                return new DailyUsage(LocalDate.now());
            }
            return existing;
        });

        long currentUsage = usage.getTotalTokens();
        long newUsage = currentUsage + estimatedTokens;

        if (newUsage > DEFAULT_DAILY_BUDGET) {
            log.warn("Tenant daily budget exceeded: tenantId={}, currentUsage={}, estimatedTokens={}, budget={}",
                    tenantId, currentUsage, estimatedTokens, DEFAULT_DAILY_BUDGET);

            return new BudgetCheckResult(
                    false,
                    "租户日预算超限：当前使用 " + currentUsage + " tokens，预估需要 " + estimatedTokens + " tokens，日预算 " + DEFAULT_DAILY_BUDGET + " tokens",
                    currentUsage,
                    DEFAULT_DAILY_BUDGET
            );
        }

        return new BudgetCheckResult(true, "Budget check passed", currentUsage, DEFAULT_DAILY_BUDGET);
    }

    /**
     * 检查任务预算
     *
     * @param taskId 任务ID
     * @param estimatedTokens 预估 token 使用量
     * @return 预算检查结果
     */
    public BudgetCheckResult checkTaskBudget(Long taskId, long estimatedTokens) {
        if (taskId == null) {
            return new BudgetCheckResult(true, "No task ID", 0, DEFAULT_TASK_BUDGET);
        }

        AtomicLong usage = taskUsage.computeIfAbsent(taskId, id -> new AtomicLong(0));
        long currentUsage = usage.get();
        long newUsage = currentUsage + estimatedTokens;

        if (newUsage > DEFAULT_TASK_BUDGET) {
            log.warn("Task budget exceeded: taskId={}, currentUsage={}, estimatedTokens={}, budget={}",
                    taskId, currentUsage, estimatedTokens, DEFAULT_TASK_BUDGET);

            return new BudgetCheckResult(
                    false,
                    "任务预算超限：当前使用 " + currentUsage + " tokens，预估需要 " + estimatedTokens + " tokens，任务预算 " + DEFAULT_TASK_BUDGET + " tokens",
                    currentUsage,
                    DEFAULT_TASK_BUDGET
            );
        }

        return new BudgetCheckResult(true, "Budget check passed", currentUsage, DEFAULT_TASK_BUDGET);
    }

    /**
     * 记录租户 token 使用量
     *
     * @param tenantId 租户ID
     * @param tokens 使用的 token 数量
     */
    public void recordTenantUsage(Long tenantId, long tokens) {
        if (tenantId == null || tokens <= 0) {
            return;
        }

        DailyUsage usage = tenantDailyUsage.compute(tenantId, (id, existing) -> {
            if (existing == null || !existing.isSameDay()) {
                DailyUsage newUsage = new DailyUsage(LocalDate.now());
                newUsage.addTokens(tokens);
                return newUsage;
            }
            existing.addTokens(tokens);
            return existing;
        });

        log.info("Tenant token usage recorded: tenantId={}, tokens={}, dailyTotal={}",
                tenantId, tokens, usage.getTotalTokens());
    }

    /**
     * 记录任务 token 使用量
     *
     * @param taskId 任务ID
     * @param tokens 使用的 token 数量
     */
    public void recordTaskUsage(Long taskId, long tokens) {
        if (taskId == null || tokens <= 0) {
            return;
        }

        AtomicLong usage = taskUsage.computeIfAbsent(taskId, id -> new AtomicLong(0));
        long newTotal = usage.addAndGet(tokens);

        log.info("Task token usage recorded: taskId={}, tokens={}, taskTotal={}",
                taskId, tokens, newTotal);
    }

    /**
     * 获取租户日使用量
     *
     * @param tenantId 租户ID
     * @return 日使用量
     */
    public long getTenantDailyUsage(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }

        DailyUsage usage = tenantDailyUsage.get(tenantId);
        if (usage == null || !usage.isSameDay()) {
            return 0;
        }

        return usage.getTotalTokens();
    }

    /**
     * 获取任务使用量
     *
     * @param taskId 任务ID
     * @return 任务使用量
     */
    public long getTaskUsage(Long taskId) {
        if (taskId == null) {
            return 0;
        }

        AtomicLong usage = taskUsage.get(taskId);
        return usage != null ? usage.get() : 0;
    }

    /**
     * 清理过期的租户使用量记录
     * 可以定期调用此方法清理过期数据
     */
    public void cleanupExpiredUsage() {
        tenantDailyUsage.entrySet().removeIf(entry -> !entry.getValue().isSameDay());
        log.info("Cleaned up expired tenant usage records");
    }

    /**
     * 清理已完成的任务使用量记录
     *
     * @param taskId 任务ID
     */
    public void cleanupTaskUsage(Long taskId) {
        if (taskId != null) {
            taskUsage.remove(taskId);
            log.info("Cleaned up task usage record: taskId={}", taskId);
        }
    }
}
