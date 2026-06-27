package com.xiaoai.agent.task.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "xiaoai.task-event.retention")
public class TaskEventRetentionProperties {

    private boolean enabled = false;

    private long fixedDelayMs = 300_000L;

    private long toolDeniedRetentionDays = 90L;
}
