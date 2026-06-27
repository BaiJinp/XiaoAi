package com.xiaoai.agent.runtime.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("runtime_run_snapshot")
public class RuntimeRunSnapshot extends TenantEntity {
    private Long taskId;

    private Long runId;

    private String snapshotType;

    private String snapshotJson;
}
