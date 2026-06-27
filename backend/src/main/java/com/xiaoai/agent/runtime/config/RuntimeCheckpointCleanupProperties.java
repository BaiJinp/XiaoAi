package com.xiaoai.agent.runtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "xiaoai.runtime.checkpoint.cleanup")
public class RuntimeCheckpointCleanupProperties {
    private boolean enabled = false;

    private long fixedDelayMs = 300_000L;

    private long suspendedExpireHours = 24L;

    private long resumingStaleMinutes = 30L;

    private long completedRetentionHours = 168L;
}
