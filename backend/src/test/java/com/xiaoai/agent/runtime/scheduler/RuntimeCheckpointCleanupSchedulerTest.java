package com.xiaoai.agent.runtime.scheduler;

import com.xiaoai.agent.runtime.config.RuntimeCheckpointCleanupProperties;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RuntimeCheckpointCleanupSchedulerTest {

    private final RuntimeCheckpointService runtimeCheckpointService = mock(RuntimeCheckpointService.class);

    @Test
    void cleanupShouldSkipWhenDisabled() {
        RuntimeCheckpointCleanupProperties properties = new RuntimeCheckpointCleanupProperties();
        properties.setEnabled(false);
        RuntimeCheckpointCleanupScheduler scheduler = new RuntimeCheckpointCleanupScheduler(
                runtimeCheckpointService,
                properties
        );

        scheduler.cleanup();

        verify(runtimeCheckpointService, never()).markSuspendedExpired(any(OffsetDateTime.class));
        verify(runtimeCheckpointService, never()).retryStaleResuming(any(OffsetDateTime.class));
        verify(runtimeCheckpointService, never()).removeCompletedBefore(any(OffsetDateTime.class));
    }

    @Test
    void cleanupShouldRunAllCheckpointCleanupStepsWhenEnabled() {
        RuntimeCheckpointCleanupProperties properties = new RuntimeCheckpointCleanupProperties();
        properties.setEnabled(true);
        properties.setSuspendedExpireHours(24);
        properties.setResumingStaleMinutes(30);
        properties.setCompletedRetentionHours(168);
        RuntimeCheckpointCleanupScheduler scheduler = new RuntimeCheckpointCleanupScheduler(
                runtimeCheckpointService,
                properties
        );

        scheduler.cleanup();

        verify(runtimeCheckpointService).markSuspendedExpired(any(OffsetDateTime.class));
        verify(runtimeCheckpointService).retryStaleResuming(any(OffsetDateTime.class));
        verify(runtimeCheckpointService).removeCompletedBefore(any(OffsetDateTime.class));
    }
}
