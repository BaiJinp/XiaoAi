package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("collaboration_session")
public class CollaborationSession extends TenantEntity {

    private String sessionCode;

    private Long templateId;

    private Long rootTaskId;

    private String strategyType;

    private String goalText;

    private String status;

    private String currentStageCode;

    private String contextJson;
}
