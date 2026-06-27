package com.xiaoai.agent.scheduled.scheduler;

import com.xiaoai.agent.scheduled.entity.ScheduledTask;
import com.xiaoai.agent.scheduled.service.ScheduledTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 定时任务调度器
 * 定期检查并执行到期的任务
 */
@Component
public class TaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public TaskScheduler(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * 每分钟检查一次到期任务
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void checkAndExecuteTasks() {
        log.debug("Checking for scheduled tasks to execute...");

        try {
            // 获取所有租户（这里简化处理，实际应该遍历所有租户）
            List<Long> tenantIds = List.of(100L); // TODO: 从配置或数据库获取

            for (Long tenantId : tenantIds) {
                List<ScheduledTask> enabledTasks = scheduledTaskService.getEnabledTasks(tenantId);

                for (ScheduledTask task : enabledTasks) {
                    if (isDueForExecution(task)) {
                        log.info("Executing scheduled task: id={}, code={}, name={}",
                                task.getId(), task.getTaskCode(), task.getTaskName());

                        try {
                            // 执行任务
                            scheduledTaskService.executeNow(task.getId());

                            // 记录成功
                            scheduledTaskService.recordExecution(task.getId(), true);

                            log.info("Task executed successfully: id={}", task.getId());

                        } catch (Exception e) {
                            log.error("Failed to execute task: id={}", task.getId(), e);
                            // 记录失败
                            scheduledTaskService.recordExecution(task.getId(), false);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error in task scheduler", e);
        }
    }

    /**
     * 检查任务是否到期
     */
    private boolean isDueForExecution(ScheduledTask task) {
        if (task.getNextExecutionTime() == null) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        return now.isAfter(task.getNextExecutionTime()) || now.isEqual(task.getNextExecutionTime());
    }
}
