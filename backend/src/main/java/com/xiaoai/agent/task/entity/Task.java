package com.xiaoai.agent.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("task")
public class Task extends TenantEntity {

    private String taskCode;

    private Long agentId;

    private Long agentVersionId;

    private Long userId;

    private String channelType;

    private String title;

    private String inputText;

    private String taskType;

    private String complexity;

    private String status;

    private Long currentRunId;

    private String resultSummary;
}
