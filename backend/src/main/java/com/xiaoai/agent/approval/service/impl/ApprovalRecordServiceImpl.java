package com.xiaoai.agent.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.approval.entity.ApprovalRecord;
import com.xiaoai.agent.approval.mapper.ApprovalRecordMapper;
import com.xiaoai.agent.approval.service.ApprovalRecordService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class ApprovalRecordServiceImpl extends ServiceImpl<ApprovalRecordMapper, ApprovalRecord> implements ApprovalRecordService {

    @Override
public ApprovalRecord getApprovalRecord(Long approvalRecordId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ApprovalRecord record = getBaseMapper().selectOne(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getTenantId, tenantId)
                .eq(ApprovalRecord::getId, approvalRecordId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Approval record not found");
        }
        return record;
    }
}
