package com.xiaoai.agent.runtime.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("runtime_checkpoint")
public class RuntimeCheckpoint extends TenantEntity {
    private Long taskId;

    private Long runId;

    private String checkpointType;

    private String checkpointStatus;

    private Long approvalRequestId;

    private String payloadJson;

    private String resumePayloadJson;
}
