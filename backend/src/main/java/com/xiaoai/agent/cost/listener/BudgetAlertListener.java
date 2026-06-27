package com.xiaoai.agent.cost.listener;

import com.xiaoai.agent.cost.entity.UsageRecord;
import com.xiaoai.agent.cost.service.UsageRecordService;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.safety.TokenBudgetTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预算告警监听器
 * <p>
 * 定期检查预算使用率，在使用率达到 80% 和 100% 时发布告警事件。
 * 集成到 RuntimeGateway 中，在每次模型/工具调用后触发检查。
 * </p>
 */
@Component
public class BudgetAlertListener {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertListener.class);

    /**
     * 告警阈值：80% 预警
     */
    private static final double WARNING_THRESHOLD = 0.8;

    /**
     * 告警阈值：100% 阻断
     */
    private static final double BLOCK_THRESHOLD = 1.0;

    private final TokenBudgetTracker budgetTracker;
    private final ApplicationEventPublisher eventPublisher;
    private final UsageRecordService usageRecordService;

    /**
     * 已告警的租户（避免重复告警）：tenantId -> lastAlertLevel
     */
    private final Map<Long, String> tenantAlertLevel = new ConcurrentHashMap<>();

    /**
     * 已告警的任务：taskId -> lastAlertLevel
     */
    private final Map<Long, String> taskAlertLevel = new ConcurrentHashMap<>();

    @Autowired
    public BudgetAlertListener(TokenBudgetTracker budgetTracker,
                               ApplicationEventPublisher eventPublisher,
                               UsageRecordService usageRecordService) {
        this.budgetTracker = budgetTracker;
        this.eventPublisher = eventPublisher;
        this.usageRecordService = usageRecordService;
    }

    /**
     * 在模型调用后检查预算并记录使用量
     *
     * @param tenantId      租户ID
     * @param taskId        任务ID
     * @param promptTokens  prompt token 数
     * @param completionTokens completion token 数
     * @param modelId       模型ID
     */
    public void afterModelCall(Long tenantId, Long taskId, int promptTokens,
                               int completionTokens, Long modelId) {
        int totalTokens = promptTokens + completionTokens;

        // 记录使用量到 tracker
        budgetTracker.recordTenantUsage(tenantId, totalTokens);
        budgetTracker.recordTaskUsage(taskId, totalTokens);

        // 持久化使用记录
        saveUsageRecord(tenantId, taskId, totalTokens, "model_call", modelId);

        // 检查租户日预算告警
        checkAndAlertTenantBudget(tenantId);

        // 检查任务预算告警
        checkAndAlertTaskBudget(taskId);
    }

    /**
     * 在工具调用后检查预算并记录使用量
     *
     * @param tenantId  租户ID
     * @param taskId    任务ID
     * @param toolCode  工具代码
     */
    public void afterToolCall(Long tenantId, Long taskId, String toolCode) {
        // 工具调用可能不直接消耗 token，但需要记录使用
        saveUsageRecord(tenantId, taskId, 0, "tool_call:" + toolCode, null);
    }

    /**
     * 检查租户日预算是否超限，发布告警事件
     */
    private void checkAndAlertTenantBudget(Long tenantId) {
        if (tenantId == null) return;

        long usage = budgetTracker.getTenantDailyUsage(tenantId);
        long budget = TokenBudgetTracker.BudgetCheckResult.class != null ? 1_000_000L : 1_000_000L;
        double ratio = (double) usage / budget;

        String currentLevel = tenantAlertLevel.get(tenantId);

        if (ratio >= BLOCK_THRESHOLD && !"blocked".equals(currentLevel)) {
            tenantAlertLevel.put(tenantId, "blocked");
            BudgetAlertEvent event = new BudgetAlertEvent(
                    this, tenantId, null, "tenant_daily",
                    "blocked", usage, budget,
                    "租户日预算已耗尽，后续模型调用将被阻断"
            );
            eventPublisher.publishEvent(event);
            log.warn("BLOCKED: Tenant daily budget exhausted: tenantId={}, usage={}, budget={}",
                    tenantId, usage, budget);
        } else if (ratio >= WARNING_THRESHOLD && !"warning".equals(currentLevel) && !"blocked".equals(currentLevel)) {
            tenantAlertLevel.put(tenantId, "warning");
            BudgetAlertEvent event = new BudgetAlertEvent(
                    this, tenantId, null, "tenant_daily",
                    "warning", usage, budget,
                    "租户日预算使用率已达 " + Math.round(ratio * 100) + "%"
            );
            eventPublisher.publishEvent(event);
            log.warn("WARNING: Tenant daily budget at " + Math.round(ratio * 100) + "%: tenantId={}, usage={}, budget={}",
                    tenantId, usage, budget);
        }
    }

    /**
     * 检查任务预算是否超限，发布告警事件
     */
    private void checkAndAlertTaskBudget(Long taskId) {
        if (taskId == null) return;

        long usage = budgetTracker.getTaskUsage(taskId);
        long budget = 100_000L;
        double ratio = (double) usage / budget;

        String currentLevel = taskAlertLevel.get(taskId);

        if (ratio >= BLOCK_THRESHOLD && !"blocked".equals(currentLevel)) {
            taskAlertLevel.put(taskId, "blocked");
            BudgetAlertEvent event = new BudgetAlertEvent(
                    this, null, taskId, "task",
                    "blocked", usage, budget,
                    "任务预算已耗尽，后续模型调用将被阻断"
            );
            eventPublisher.publishEvent(event);
            log.warn("BLOCKED: Task budget exhausted: taskId={}, usage={}, budget={}",
                    taskId, usage, budget);
        } else if (ratio >= WARNING_THRESHOLD && !"warning".equals(currentLevel) && !"blocked".equals(currentLevel)) {
            taskAlertLevel.put(taskId, "warning");
            BudgetAlertEvent event = new BudgetAlertEvent(
                    this, null, taskId, "task",
                    "warning", usage, budget,
                    "任务预算使用率已达 " + Math.round(ratio * 100) + "%"
            );
            eventPublisher.publishEvent(event);
            log.warn("WARNING: Task budget at " + Math.round(ratio * 100) + "%: taskId={}, usage={}, budget={}",
                    taskId, usage, budget);
        }
    }

    /**
     * 持久化使用记录
     */
    private void saveUsageRecord(Long tenantId, Long taskId, int tokens,
                                 String usageType, Long modelId) {
        try {
            if (usageRecordService == null) return;

            UsageRecord record = new UsageRecord();
            record.setTenantId(tenantId);
            record.setTaskId(taskId);
            record.setUsageType(usageType);
            record.setTokenCount(tokens);
            record.setUsageAmount(java.math.BigDecimal.valueOf(tokens));
            record.setUsageDate(java.time.LocalDate.now());
            if (modelId != null) {
                record.setDetailJson("{\"modelId\":" + modelId + "}");
            }

            usageRecordService.save(record);
        } catch (Exception e) {
            log.error("Failed to save usage record: tenantId={}, taskId={}, type={}",
                    tenantId, taskId, usageType, e);
        }
    }

    /**
     * 每日清理告警状态
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyAlerts() {
        tenantAlertLevel.clear();
        budgetTracker.cleanupExpiredUsage();
        log.info("Daily budget alerts reset");
    }

    /**
     * 预算告警事件
     */
    public static class BudgetAlertEvent extends org.springframework.context.ApplicationEvent {
        private final Long tenantId;
        private final Long taskId;
        private final String scope;       // tenant_daily / task
        private final String level;       // warning / blocked
        private final long usage;
        private final long budget;
        private final String message;

        public BudgetAlertEvent(Object source, Long tenantId, Long taskId,
                                String scope, String level, long usage, long budget, String message) {
            super(source);
            this.tenantId = tenantId;
            this.taskId = taskId;
            this.scope = scope;
            this.level = level;
            this.usage = usage;
            this.budget = budget;
            this.message = message;
        }

        public Long getTenantId() { return tenantId; }
        public Long getTaskId() { return taskId; }
        public String getScope() { return scope; }
        public String getLevel() { return level; }
        public long getUsage() { return usage; }
        public long getBudget() { return budget; }
        public String getMessage() { return message; }
    }
}
