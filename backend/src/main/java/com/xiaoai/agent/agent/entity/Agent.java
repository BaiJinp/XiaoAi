package com.xiaoai.agent.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("agent")
public class Agent extends TenantEntity {

    private String agentCode;

    private String agentName;

    private String agentType;

    private String description;

    private String rolePrompt;

    private String responsibilityText;

    private String boundaryText;

    private String capabilityJson;

    private String toolPolicyJson;

    private String contextPolicyJson;

    private String memoryPolicyJson;

    private String orchestrationPolicyJson;

    private String status;

    private Long currentVersionId;

    private Long latestStableVersionId;

    private Long ownerUserId;

    private String configJson;
}
