package com.xiaoai.agent.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("task_run")
public class TaskRun extends TenantEntity {
    private String runCode;

    private Long taskId;

    private Long agentId;

    private Long agentVersionId;

    private Long runtimeNodeId;

    private String runtimeType;

    private String status;

    private OffsetDateTime startTime;

    private OffsetDateTime endTime;

    private String suspendReason;

    private String failReason;

    private String traceId;

    private String snapshotJson;

    private String usageJson;
}
