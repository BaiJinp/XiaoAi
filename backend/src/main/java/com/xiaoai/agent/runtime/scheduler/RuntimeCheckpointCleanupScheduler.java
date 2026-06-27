package com.xiaoai.agent.runtime.scheduler;

import com.xiaoai.agent.runtime.config.RuntimeCheckpointCleanupProperties;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class RuntimeCheckpointCleanupScheduler {

    private final RuntimeCheckpointService runtimeCheckpointService;
    private final RuntimeCheckpointCleanupProperties properties;

    public RuntimeCheckpointCleanupScheduler(RuntimeCheckpointService runtimeCheckpointService,
                                             RuntimeCheckpointCleanupProperties properties) {
        this.runtimeCheckpointService = runtimeCheckpointService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${xiaoai.runtime.checkpoint.cleanup.fixed-delay-ms:300000}")
public void cleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        runtimeCheckpointService.markSuspendedExpired(now.minusHours(properties.getSuspendedExpireHours()));
        runtimeCheckpointService.retryStaleResuming(now.minusMinutes(properties.getResumingStaleMinutes()));
        runtimeCheckpointService.removeCompletedBefore(now.minusHours(properties.getCompletedRetentionHours()));
    }
}
