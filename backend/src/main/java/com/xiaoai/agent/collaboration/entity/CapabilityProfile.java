package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("capability_profile")
public class CapabilityProfile extends TenantEntity {

    private String profileCode;

    private String profileName;

    private Long roleId;

    private String capabilityJson;

    private String toolScopeJson;

    private String knowledgeScopeJson;

    private String policyScopeJson;

    private String costLimitJson;

    private String status;
}
