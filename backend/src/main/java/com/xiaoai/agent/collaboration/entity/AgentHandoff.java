package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_handoff")
public class AgentHandoff extends TenantEntity {

    private Long sessionId;

    private Long fromThreadId;

    private Long toThreadId;

    private Long artifactId;

    private String handoffType;

    private String status;

    private String messageText;

    private String metadataJson;
}
