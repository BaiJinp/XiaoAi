package com.xiaoai.agent.approval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.model.ApprovalRequestResponse;
import com.xiaoai.agent.approval.model.CreateApprovalRequestCommand;
import com.xiaoai.agent.approval.model.HandleApprovalCommand;

public interface ApprovalRequestService extends IService<ApprovalRequest> {

    ApprovalRequest getApprovalRequest(Long approvalRequestId);

    ApprovalRequestResponse createApproval(CreateApprovalRequestCommand command);

    void handleApproval(Long approvalRequestId, HandleApprovalCommand command);
}
