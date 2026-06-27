package com.xiaoai.agent.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("approval_request")
public class ApprovalRequest extends TenantEntity {
    private String requestCode;

    private Long taskId;

    private Long runId;

    private Long stepId;

    private Long applicantUserId;

    private Long approverUserId;

    private String approvalType;

    private String approvalStatus;

    private String reason;

    private String requestPayloadJson;

    private OffsetDateTime expireAt;

    private OffsetDateTime approvedAt;

    private OffsetDateTime rejectedAt;
}
