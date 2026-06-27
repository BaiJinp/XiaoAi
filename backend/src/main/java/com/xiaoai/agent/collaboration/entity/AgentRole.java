package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_role")
public class AgentRole extends TenantEntity {

    private String roleCode;

    private String roleName;

    private String domainCode;

    private String description;

    private String responsibilityText;

    private String inputArtifactTypes;

    private String outputArtifactTypes;

    private Long defaultAgentId;

    private Long defaultAgentVersionId;

    private String status;
}
