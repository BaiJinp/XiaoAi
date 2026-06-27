package com.xiaoai.agent.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("task_artifact")
public class TaskArtifact extends TenantEntity {
    private Long taskId;

    private Long runId;

    private String artifactType;

    private String artifactName;

    private String contentText;

    private String storageUrl;

    private String metadataJson;
}
