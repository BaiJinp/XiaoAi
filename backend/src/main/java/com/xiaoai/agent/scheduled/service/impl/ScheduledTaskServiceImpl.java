package com.xiaoai.agent.scheduled.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.scheduled.entity.ScheduledTask;
import com.xiaoai.agent.scheduled.mapper.ScheduledTaskMapper;
import com.xiaoai.agent.scheduled.service.ScheduledTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 定时任务服务实现
 */
@Service
public class ScheduledTaskServiceImpl extends ServiceImpl<ScheduledTaskMapper, ScheduledTask>
        implements ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskServiceImpl.class);

    private final ModelGateway modelGateway;

    @Autowired
    public ScheduledTaskServiceImpl(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
public ScheduledTask createTask(Long tenantId, String taskCode, String taskName,
                                     String description, String cronExpression,
                                     String naturalLanguage, String taskType,
                                     Long agentId, String taskConfig,
                                     String deliveryTargets, Long userId) {
        ScheduledTask task = new ScheduledTask();
        task.setTenantId(tenantId);
        task.setTaskCode(taskCode);
        task.setTaskName(taskName);
        task.setDescription(description);
        task.setCronExpression(cronExpression);
        task.setNaturalLanguage(naturalLanguage);
        task.setTaskType(taskType != null ? taskType : "custom");
        task.setAgentId(agentId);
        task.setTaskConfig(taskConfig);
        task.setDeliveryTargets(deliveryTargets);
        task.setCreatedByUserId(userId);
        task.setExecutionCount(0);
        task.setSuccessCount(0);
        task.setFailureCount(0);
        task.setIsEnabled(true);
        task.setStatus("active");
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());

        // 计算下次执行时间
        if (cronExpression != null) {
            try {
                CronExpression cron = CronExpression.parse(cronExpression);
                task.setNextExecutionTime(cron.next(OffsetDateTime.now()));
            } catch (Exception e) {
                log.error("Invalid cron expression: {}", cronExpression, e);
            }
        }

        save(task);
        log.info("Created scheduled task: code={}, name={}, cron={}",
                taskCode, taskName, cronExpression);

        return task;
    }

    @Override
public ScheduledTask getByCode(Long tenantId, String taskCode) {
        return getOne(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getTenantId, tenantId)
                .eq(ScheduledTask::getTaskCode, taskCode));
    }

    @Override
public List<ScheduledTask> getUserTasks(Long tenantId, Long userId) {
        return list(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getTenantId, tenantId)
                .eq(ScheduledTask::getCreatedByUserId, userId)
                .orderByDesc(ScheduledTask::getCreatedAt));
    }

    @Override
public List<ScheduledTask> getAgentTasks(Long tenantId, Long agentId) {
        return list(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getTenantId, tenantId)
                .eq(ScheduledTask::getAgentId, agentId)
                .orderByDesc(ScheduledTask::getCreatedAt));
    }

    @Override
public List<ScheduledTask> getEnabledTasks(Long tenantId) {
        return list(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getTenantId, tenantId)
                .eq(ScheduledTask::getIsEnabled, true)
                .eq(ScheduledTask::getStatus, "active"));
    }

    @Override
public void setEnabled(Long taskId, boolean enabled) {
        ScheduledTask task = getById(taskId);
        if (task != null) {
            task.setIsEnabled(enabled);
            task.setUpdatedAt(OffsetDateTime.now());
            updateById(task);
            log.info("Set task enabled: id={}, enabled={}", taskId, enabled);
        }
    }

    @Override
public void pauseTask(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task != null) {
            task.setStatus("paused");
            task.setIsEnabled(false);
            task.setUpdatedAt(OffsetDateTime.now());
            updateById(task);
            log.info("Paused task: id={}", taskId);
        }
    }

    @Override
public void resumeTask(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task != null) {
            task.setStatus("active");
            task.setIsEnabled(true);
            task.setUpdatedAt(OffsetDateTime.now());
            updateById(task);
            log.info("Resumed task: id={}", taskId);
        }
    }

    @Override
public void executeNow(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task != null) {
            log.info("Executing task now: id={}, code={}", taskId, task.getTaskCode());
            // TODO: 实际执行任务逻辑
            recordExecution(taskId, true);
        }
    }

    @Override
public void updateNextExecutionTime(Long taskId) {
        ScheduledTask task = getById(taskId);
        if (task != null && task.getCronExpression() != null) {
            try {
                CronExpression cron = CronExpression.parse(task.getCronExpression());
                task.setNextExecutionTime(cron.next(OffsetDateTime.now()));
                task.setUpdatedAt(OffsetDateTime.now());
                updateById(task);
            } catch (Exception e) {
                log.error("Failed to update next execution time: taskId={}", taskId, e);
            }
        }
    }

    @Override
public void recordExecution(Long taskId, boolean success) {
        ScheduledTask task = getById(taskId);
        if (task != null) {
            task.setLastExecutionTime(OffsetDateTime.now());
            task.setExecutionCount(task.getExecutionCount() + 1);
            if (success) {
                task.setSuccessCount(task.getSuccessCount() + 1);
            } else {
                task.setFailureCount(task.getFailureCount() + 1);
            }
            task.setUpdatedAt(OffsetDateTime.now());
            updateById(task);

            // 更新下次执行时间
            updateNextExecutionTime(taskId);
        }
    }

    @Override
public ScheduledTask createFromNaturalLanguage(Long tenantId, String naturalLanguage,
                                                    String taskType, Long agentId,
                                                    String taskConfig, Long userId) {
        // 解析自然语言为 Cron 表达式
        String cronExpression = parseNaturalLanguage(naturalLanguage);

        String taskCode = "scheduled_" + System.currentTimeMillis();
        String taskName = naturalLanguage.length() > 50 ? naturalLanguage.substring(0, 50) + "..." : naturalLanguage;

        return createTask(tenantId, taskCode, taskName, naturalLanguage,
                cronExpression, naturalLanguage, taskType, agentId, taskConfig, null, userId);
    }

    @Override
public String parseNaturalLanguage(String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.isEmpty()) {
            return null;
        }

        try {
            // 调用模型解析自然语言
            String prompt = String.format("""
                    将以下自然语言描述转换为 Cron 表达式：

                    描述：%s

                    只返回 Cron 表达式，不要其他内容。

                    示例：
                    - "每天早上9点" -> "0 0 9 * * ?"
                    - "每周一上午10点" -> "0 0 10 ? * MON"
                    - "每小时" -> "0 0 * * * ?"
                    - "每天下午6点" -> "0 0 18 * * ?"
                    """, naturalLanguage);

            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L);
            command.setPrompt(prompt);

            ChatModelResponse response = modelGateway.chat(command);
            String cronExpression = response.getContent().trim();

            // 验证 Cron 表达式
            CronExpression.parse(cronExpression);

            return cronExpression;

        } catch (Exception e) {
            log.error("Failed to parse natural language: {}", naturalLanguage, e);
            return null;
        }
    }

    @Override
public void deleteTask(Long taskId) {
        removeById(taskId);
        log.info("Deleted scheduled task: id={}", taskId);
    }
}
