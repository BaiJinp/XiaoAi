package com.xiaoai.agent.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("approval_record")
public class ApprovalRecord extends TenantEntity {
    private Long approvalRequestId;

    private Long operatorUserId;

    private String action;

    private String commentText;

    private String actionPayloadJson;
}
