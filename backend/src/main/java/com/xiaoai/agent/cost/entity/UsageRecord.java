package com.xiaoai.agent.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.math.BigDecimal;

@Getter
@Setter
@TableName("usage_record")
public class UsageRecord extends TenantEntity {
    private Long userId;

    private Long agentId;

    private Long taskId;

    private Long runId;

    private String usageType;

    private BigDecimal usageAmount;

    private Integer tokenCount;

    private BigDecimal costAmount;

    private LocalDate usageDate;

    private String detailJson;
}
