package com.xiaoai.agent.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_knowledge_binding")
public class AgentKnowledgeBinding extends TenantEntity {
    private Long agentId;

    private Long agentVersionId;

    private Long knowledgeBaseId;

    private String bindingStatus;

    private String policyJson;
}
