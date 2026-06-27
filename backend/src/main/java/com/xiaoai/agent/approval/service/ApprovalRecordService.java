package com.xiaoai.agent.approval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.approval.entity.ApprovalRecord;

public interface ApprovalRecordService extends IService<ApprovalRecord> {

    ApprovalRecord getApprovalRecord(Long approvalRecordId);
}
