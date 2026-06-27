package com.xiaoai.agent.memory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_memory")
public class AgentMemory extends TenantEntity {

    private String memoryCode;

    private Long agentId;

    private Long agentVersionId;

    private Long taskId;

    private Long runId;

    private Long sessionId;

    private Long userId;

    private String memoryType;

    private String memoryScope;

    private String summaryText;

    private String sourceText;

    private String confidence;

    private String status;

    private String policyJson;
}
