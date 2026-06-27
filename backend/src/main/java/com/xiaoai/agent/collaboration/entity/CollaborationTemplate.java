package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("collaboration_template")
public class CollaborationTemplate extends TenantEntity {

    private String templateCode;

    private String templateName;

    private String domainCode;

    private String strategyType;

    private String templateJson;

    private String status;
}
