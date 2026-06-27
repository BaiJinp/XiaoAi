package com.xiaoai.agent.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("task_step")
public class TaskStep extends TenantEntity {
    private Long taskId;

    private Long runId;

    private Long parentStepId;

    private String stepCode;

    private String stepName;

    private String stepType;

    private String status;

    private Integer sortOrder;

    private String inputJson;

    private String outputJson;

    private String failReason;

    private OffsetDateTime startTime;

    private OffsetDateTime endTime;
}
