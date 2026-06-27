package com.xiaoai.agent.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_publish_scope")
public class AgentPublishScope extends TenantEntity {
    private Long agentId;

    private Long agentVersionId;

    private String scopeType;

    private String scopeValue;

    private String status;
}
