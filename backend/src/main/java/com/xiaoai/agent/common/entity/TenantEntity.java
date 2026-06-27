package com.xiaoai.agent.common.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class TenantEntity extends BaseEntity {

    private Long tenantId;
}
