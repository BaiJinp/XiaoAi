package com.xiaoai.agent.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@TableName("budget_policy")
public class BudgetPolicy extends TenantEntity {
    private String policyCode;

    private String policyName;

    private String targetType;

    private Long targetId;

    private Integer dailyTokenLimit;

    private Integer taskTokenLimit;

    private BigDecimal dailyCostLimit;

    private String policyJson;

    private String status;
}
