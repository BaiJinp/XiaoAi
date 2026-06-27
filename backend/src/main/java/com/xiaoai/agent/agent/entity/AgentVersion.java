package com.xiaoai.agent.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_version")
public class AgentVersion extends TenantEntity {
    private Long agentId;

    private String versionNo;

    private String versionStatus;

    private String rolePrompt;

    private String responsibilityText;

    private String boundaryText;

    private String configJson;

    private String knowledgeScopeJson;

    private String toolScopeJson;

    private String permissionPolicyJson;

    private String budgetPolicyJson;

    private String runtimeSnapshotJson;
}
