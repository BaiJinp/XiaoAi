package com.xiaoai.agent.task.scheduler;

import com.xiaoai.agent.task.config.TaskEventRetentionProperties;
import com.xiaoai.agent.task.service.TaskEventService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskEventRetentionSchedulerTest {

    private final TaskEventService taskEventService = mock(TaskEventService.class);

    @Test
    void cleanupShouldSkipWhenDisabled() {
        TaskEventRetentionProperties properties = new TaskEventRetentionProperties();
        properties.setEnabled(false);
        TaskEventRetentionScheduler scheduler = new TaskEventRetentionScheduler(taskEventService, properties);

        scheduler.cleanup();

        verify(taskEventService, never()).removeToolDeniedBefore(any(OffsetDateTime.class));
    }

    @Test
    void cleanupShouldRemoveToolDeniedEventsWhenEnabled() {
        TaskEventRetentionProperties properties = new TaskEventRetentionProperties();
        properties.setEnabled(true);
        properties.setToolDeniedRetentionDays(30);
        TaskEventRetentionScheduler scheduler = new TaskEventRetentionScheduler(taskEventService, properties);

        scheduler.cleanup();

        verify(taskEventService).removeToolDeniedBefore(any(OffsetDateTime.class));
    }
}
