package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent_thread")
public class AgentThread extends TenantEntity {

    private Long sessionId;

    private Long parentThreadId;

    private Long taskId;

    private Long agentId;

    private Long agentVersionId;

    private Long roleId;

    private String threadCode;

    private String threadName;

    private String status;

    private Long inputArtifactId;

    private Long outputArtifactId;

    private String contextJson;
}
