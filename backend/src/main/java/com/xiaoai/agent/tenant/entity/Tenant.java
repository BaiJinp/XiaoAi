package com.xiaoai.agent.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("tenant")
public class Tenant extends BaseEntity {

    private String tenantCode;

    private String tenantName;

    private String status;

    private String configJson;
}
