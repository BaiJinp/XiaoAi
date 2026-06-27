package com.xiaoai.agent.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_tool_binding")
public class AgentToolBinding extends TenantEntity {
    private Long agentId;

    private Long agentVersionId;

    private Long toolId;

    private String bindingStatus;

    private String policyJson;
}
