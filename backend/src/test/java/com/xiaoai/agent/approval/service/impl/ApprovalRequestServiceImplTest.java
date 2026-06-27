package com.xiaoai.agent.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.approval.entity.ApprovalRecord;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.event.ApprovalHandledEvent;
import com.xiaoai.agent.approval.mapper.ApprovalRequestMapper;
import com.xiaoai.agent.approval.model.ApprovalRequestResponse;
import com.xiaoai.agent.approval.model.CreateApprovalRequestCommand;
import com.xiaoai.agent.approval.model.HandleApprovalCommand;
import com.xiaoai.agent.approval.service.ApprovalRecordService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRequestServiceImplTest {

    private final ApprovalRequestMapper approvalRequestMapper = mock(ApprovalRequestMapper.class);
    private final ApprovalRecordService approvalRecordService = mock(ApprovalRecordService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ApprovalRequestServiceImpl approvalRequestService = new ApprovalRequestServiceImpl(
            approvalRecordService,
            eventPublisher
    );

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(approvalRequestService, approvalRequestMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-approval")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createApprovalShouldPersistPendingRequest() {
        when(approvalRequestMapper.insert(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest request = invocation.getArgument(0);
            request.setId(88L);
            return 1;
        });
        CreateApprovalRequestCommand command = new CreateApprovalRequestCommand();
        command.setTaskId(1L);
        command.setRunId(99L);
        command.setStepId(9L);
        command.setApplicantUserId(200L);
        command.setApproverUserId(300L);
        command.setApprovalType("tool_call");
        command.setReason("需要审批");

        ApprovalRequestResponse response = approvalRequestService.createApproval(command);

        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestMapper).insert(captor.capture());
        ApprovalRequest saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getTaskId()).isEqualTo(1L);
        assertThat(saved.getRunId()).isEqualTo(99L);
        assertThat(saved.getStepId()).isEqualTo(9L);
        assertThat(saved.getApplicantUserId()).isEqualTo(200L);
        assertThat(saved.getApproverUserId()).isEqualTo(300L);
        assertThat(saved.getApprovalType()).isEqualTo("tool_call");
        assertThat(saved.getApprovalStatus()).isEqualTo("pending");
        assertThat(saved.getRequestPayloadJson()).isEqualTo("{}");
        assertThat(saved.getRequestCode()).startsWith("APR-");
        assertThat(response.getApprovalRequestId()).isEqualTo(88L);
        assertThat(response.getApprovalStatus()).isEqualTo("pending");
    }

    @Test
    void handleApprovalShouldApproveRequestAndSaveRecord() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(88L);
        request.setTenantId(100L);
        request.setApprovalStatus("pending");
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(request);
        when(approvalRequestMapper.updateById(any(ApprovalRequest.class))).thenReturn(1);
        HandleApprovalCommand command = new HandleApprovalCommand();
        command.setOperatorUserId(300L);
        command.setAction("approve");
        command.setCommentText("同意");

        approvalRequestService.handleApproval(88L, command);

        assertThat(request.getApprovalStatus()).isEqualTo("approved");
        assertThat(request.getApprovedAt()).isNotNull();
        ArgumentCaptor<ApprovalRecord> captor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRequestMapper).updateById(request);
        verify(approvalRecordService).save(captor.capture());
        ApprovalRecord record = captor.getValue();
        assertThat(record.getTenantId()).isEqualTo(100L);
        assertThat(record.getApprovalRequestId()).isEqualTo(88L);
        assertThat(record.getOperatorUserId()).isEqualTo(300L);
        assertThat(record.getAction()).isEqualTo("approve");
        assertThat(record.getCommentText()).isEqualTo("同意");
        assertThat(record.getActionPayloadJson()).isEqualTo("{}");
        ArgumentCaptor<ApprovalHandledEvent> eventCaptor = ArgumentCaptor.forClass(ApprovalHandledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getApprovalRequestId()).isEqualTo(88L);
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("approve");
    }

    @Test
    void handleApprovalShouldRejectMissingOrCrossTenantRequest() {
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        HandleApprovalCommand command = new HandleApprovalCommand();
        command.setOperatorUserId(300L);
        command.setAction("approve");

        assertThatThrownBy(() -> approvalRequestService.handleApproval(88L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approval request not found");

        verify(approvalRequestMapper, never()).updateById(any(ApprovalRequest.class));
        verify(approvalRecordService, never()).save(any(ApprovalRecord.class));
    }

    @Test
    void handleApprovalShouldRejectCrossTenantRequest() {
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        HandleApprovalCommand command = new HandleApprovalCommand();
        command.setOperatorUserId(300L);
        command.setAction("approve");

        assertThatThrownBy(() -> approvalRequestService.handleApproval(88L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approval request not found");

        verify(approvalRequestMapper, never()).updateById(any(ApprovalRequest.class));
        verify(approvalRecordService, never()).save(any(ApprovalRecord.class));
    }

    @Test
    void getApprovalRequestShouldReturnTenantScopedRequest() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(88L);
        request.setTenantId(100L);
        request.setApprovalStatus("pending");
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(request);

        ApprovalRequest result = approvalRequestService.getApprovalRequest(88L);

        assertThat(result.getApprovalStatus()).isEqualTo("pending");
    }

    @Test
    void getApprovalRequestShouldRejectMissingOrCrossTenantRequest() {
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> approvalRequestService.getApprovalRequest(88L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approval request not found");
    }
}
