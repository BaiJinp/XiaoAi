package com.xiaoai.agent.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.approval.entity.ApprovalRecord;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.event.ApprovalHandledEvent;
import com.xiaoai.agent.approval.mapper.ApprovalRequestMapper;
import com.xiaoai.agent.approval.model.ApprovalRequestResponse;
import com.xiaoai.agent.approval.model.CreateApprovalRequestCommand;
import com.xiaoai.agent.approval.model.HandleApprovalCommand;
import com.xiaoai.agent.approval.service.ApprovalRecordService;
import com.xiaoai.agent.approval.service.ApprovalRequestService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ApprovalRequestServiceImpl extends ServiceImpl<ApprovalRequestMapper, ApprovalRequest> implements ApprovalRequestService {

    private final ApprovalRecordService approvalRecordService;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalRequestServiceImpl(ApprovalRecordService approvalRecordService,
                                      ApplicationEventPublisher eventPublisher) {
        this.approvalRecordService = approvalRecordService;
        this.eventPublisher = eventPublisher;
    }

    @Override
public ApprovalRequest getApprovalRequest(Long approvalRequestId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ApprovalRequest request = getBaseMapper().selectOne(new LambdaQueryWrapper<ApprovalRequest>()
                .eq(ApprovalRequest::getTenantId, tenantId)
                .eq(ApprovalRequest::getId, approvalRequestId)
                .last("limit 1"));
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Approval request not found");
        }
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public ApprovalRequestResponse createApproval(CreateApprovalRequestCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        ApprovalRequest request = new ApprovalRequest();
        request.setTenantId(tenantId);
        request.setRequestCode("APR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        request.setTaskId(command.getTaskId());
        request.setRunId(command.getRunId());
        request.setStepId(command.getStepId());
        request.setApplicantUserId(command.getApplicantUserId());
        request.setApproverUserId(command.getApproverUserId());
        request.setApprovalType(command.getApprovalType());
        request.setApprovalStatus("pending");
        request.setReason(command.getReason());
        request.setRequestPayloadJson(command.getRequestPayloadJson() == null ? "{}" : command.getRequestPayloadJson());
        save(request);
        return ApprovalRequestResponse.builder()
                .approvalRequestId(request.getId())
                .requestCode(request.getRequestCode())
                .approvalStatus(request.getApprovalStatus())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public void handleApproval(Long approvalRequestId, HandleApprovalCommand command) {
        UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        ApprovalRequest request = getApprovalRequest(approvalRequestId);
        if ("approve".equals(command.getAction())) {
            request.setApprovalStatus("approved");
            request.setApprovedAt(OffsetDateTime.now());
        } else if ("reject".equals(command.getAction())) {
            request.setApprovalStatus("rejected");
            request.setRejectedAt(OffsetDateTime.now());
        } else {
            request.setApprovalStatus(command.getAction());
        }
        updateById(request);

        ApprovalRecord record = new ApprovalRecord();
        record.setTenantId(request.getTenantId());
        record.setApprovalRequestId(request.getId());
        record.setOperatorUserId(command.getOperatorUserId());
        record.setAction(command.getAction());
        record.setCommentText(command.getCommentText());
        record.setActionPayloadJson(command.getActionPayloadJson() == null ? "{}" : command.getActionPayloadJson());
        approvalRecordService.save(record);

        eventPublisher.publishEvent(ApprovalHandledEvent.builder()
                .tenantId(request.getTenantId())
                .approvalRequestId(request.getId())
                .taskId(request.getTaskId())
                .runId(request.getRunId())
                .operatorUserId(command.getOperatorUserId())
                .action(command.getAction())
                .commentText(command.getCommentText())
                .build());
    }
}
