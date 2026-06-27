package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("quality_gate")
public class QualityGate extends TenantEntity {

    private Long sessionId;

    private String gateCode;

    private String gateName;

    private String gateType;

    private String status;

    private Boolean required;

    private String ruleJson;

    private String resultJson;

    private String failReason;

    private OffsetDateTime passedAt;
}
