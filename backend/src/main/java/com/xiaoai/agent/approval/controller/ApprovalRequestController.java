package com.xiaoai.agent.approval.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.model.ApprovalRequestResponse;
import com.xiaoai.agent.approval.model.CreateApprovalRequestCommand;
import com.xiaoai.agent.approval.model.HandleApprovalCommand;
import com.xiaoai.agent.approval.service.ApprovalRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/approval-requests")
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    @GetMapping("/{id}")
public ApiResponse<ApprovalRequest> getById(@PathVariable Long id) {
        return ApiResponse.success(approvalRequestService.getApprovalRequest(id));
    }

    @PostMapping
public ApiResponse<ApprovalRequestResponse> createApproval(@Valid @RequestBody CreateApprovalRequestCommand command) {
        return ApiResponse.success(approvalRequestService.createApproval(command));
    }

    @PostMapping("/{id}/handle")
public ApiResponse<Void> handleApproval(
            @PathVariable Long id,
            @Valid @RequestBody HandleApprovalCommand command) {
        approvalRequestService.handleApproval(id, command);
        return ApiResponse.success(null);
    }
}
