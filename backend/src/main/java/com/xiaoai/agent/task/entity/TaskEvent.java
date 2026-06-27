package com.xiaoai.agent.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("task_event")
public class TaskEvent extends TenantEntity {
    private Long taskId;

    private Long runId;

    private Long stepId;

    private String eventType;

    private String eventLevel;

    private String eventSummary;

    private String payloadJson;

    private String traceId;

    private OffsetDateTime occurredAt;
public Long getSequence() {
        return getId();
    }
}
