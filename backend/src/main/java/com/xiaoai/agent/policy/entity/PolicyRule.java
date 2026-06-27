package com.xiaoai.agent.policy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("policy_rule")
public class PolicyRule extends TenantEntity {
    private String ruleCode;

    private String ruleName;

    private String ruleType;

    private String targetType;

    private Long targetId;

    private String effect;

    private String conditionJson;

    private String approverJson;

    private Integer priority;

    private String status;
}
