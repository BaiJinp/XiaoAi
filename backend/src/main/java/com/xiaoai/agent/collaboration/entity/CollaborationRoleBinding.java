package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("collaboration_role_binding")
public class CollaborationRoleBinding extends TenantEntity {

    private Long templateId;

    private String roleCode;

    private String bindingScope;

    private String bindingKey;

    private Long agentId;

    private Long agentVersionId;

    private String status;
}
