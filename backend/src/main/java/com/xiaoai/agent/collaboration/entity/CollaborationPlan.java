package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("collaboration_plan")
public class CollaborationPlan extends TenantEntity {

    private Long sessionId;

    private Long generatedByThreadId;

    private String planStatus;

    private String validationStatus;

    private String planJson;

    private String validationResultJson;
}
