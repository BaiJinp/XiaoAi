package com.xiaoai.agent.runtime.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;

import java.time.OffsetDateTime;

public interface RuntimeCheckpointService extends IService<RuntimeCheckpoint> {

    boolean claimApprovedCheckpoint(Long tenantId, Long runId, Long approvalRequestId);

    int markSuspendedExpired(OffsetDateTime expireBefore);

    int retryStaleResuming(OffsetDateTime staleBefore);

    int removeCompletedBefore(OffsetDateTime completedBefore);
}
