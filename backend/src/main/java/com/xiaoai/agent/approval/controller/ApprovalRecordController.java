package com.xiaoai.agent.approval.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.approval.entity.ApprovalRecord;
import com.xiaoai.agent.approval.service.ApprovalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/approval-records")
public class ApprovalRecordController {

    private final ApprovalRecordService approvalRecordService;

    @GetMapping("/{id}")
public ApiResponse<ApprovalRecord> getById(@PathVariable Long id) {
        return ApiResponse.success(approvalRecordService.getApprovalRecord(id));
    }
}
