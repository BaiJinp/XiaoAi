package com.xiaoai.agent.runtime.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;
import com.xiaoai.agent.runtime.mapper.RuntimeCheckpointMapper;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class RuntimeCheckpointServiceImpl extends ServiceImpl<RuntimeCheckpointMapper, RuntimeCheckpoint>
        implements RuntimeCheckpointService {

    @Override
public boolean claimApprovedCheckpoint(Long tenantId, Long runId, Long approvalRequestId) {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setCheckpointStatus("resuming");
        return update(checkpoint, new LambdaUpdateWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getTenantId, tenantId)
                .eq(RuntimeCheckpoint::getRunId, runId)
                .eq(RuntimeCheckpoint::getApprovalRequestId, approvalRequestId)
                .eq(RuntimeCheckpoint::getCheckpointStatus, "approved"));
    }

    @Override
public int markSuspendedExpired(OffsetDateTime expireBefore) {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setCheckpointStatus("expired");
        return baseMapper.update(checkpoint, new LambdaUpdateWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getCheckpointStatus, "suspended")
                .lt(RuntimeCheckpoint::getUpdatedAt, expireBefore));
    }

    @Override
public int retryStaleResuming(OffsetDateTime staleBefore) {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setCheckpointStatus("approved");
        return baseMapper.update(checkpoint, new LambdaUpdateWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getCheckpointStatus, "resuming")
                .lt(RuntimeCheckpoint::getUpdatedAt, staleBefore));
    }

    @Override
public int removeCompletedBefore(OffsetDateTime completedBefore) {
        return baseMapper.delete(new LambdaUpdateWrapper<RuntimeCheckpoint>()
                .eq(RuntimeCheckpoint::getCheckpointStatus, "completed")
                .lt(RuntimeCheckpoint::getUpdatedAt, completedBefore));
    }
}
