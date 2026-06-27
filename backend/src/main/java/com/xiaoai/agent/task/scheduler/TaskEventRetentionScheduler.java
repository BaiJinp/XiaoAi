package com.xiaoai.agent.task.scheduler;

import com.xiaoai.agent.task.config.TaskEventRetentionProperties;
import com.xiaoai.agent.task.service.TaskEventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class TaskEventRetentionScheduler {

    private final TaskEventService taskEventService;
    private final TaskEventRetentionProperties properties;

    public TaskEventRetentionScheduler(TaskEventService taskEventService,
                                       TaskEventRetentionProperties properties) {
        this.taskEventService = taskEventService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${xiaoai.task-event.retention.fixed-delay-ms:300000}")
public void cleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        taskEventService.removeToolDeniedBefore(
                OffsetDateTime.now().minusDays(properties.getToolDeniedRetentionDays())
        );
    }
}
