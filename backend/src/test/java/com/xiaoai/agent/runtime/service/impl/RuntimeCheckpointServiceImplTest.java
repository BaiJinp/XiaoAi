package com.xiaoai.agent.runtime.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;
import com.xiaoai.agent.runtime.mapper.RuntimeCheckpointMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeCheckpointServiceImplTest {

    private final RuntimeCheckpointMapper runtimeCheckpointMapper = mock(RuntimeCheckpointMapper.class);
    private final RuntimeCheckpointServiceImpl runtimeCheckpointService = new RuntimeCheckpointServiceImpl();

    RuntimeCheckpointServiceImplTest() {
        TestReflectionUtils.injectBaseMapper(runtimeCheckpointService, runtimeCheckpointMapper);
    }

    @Test
    void claimApprovedCheckpointShouldUpdateApprovedToResuming() {
        when(runtimeCheckpointMapper.update(any(RuntimeCheckpoint.class), any(Wrapper.class))).thenReturn(1);

        boolean claimed = runtimeCheckpointService.claimApprovedCheckpoint(100L, 99L, 88L);

        ArgumentCaptor<RuntimeCheckpoint> captor = ArgumentCaptor.forClass(RuntimeCheckpoint.class);
        verify(runtimeCheckpointMapper).update(captor.capture(), any(Wrapper.class));
        assertThat(claimed).isTrue();
        assertThat(captor.getValue().getCheckpointStatus()).isEqualTo("resuming");
    }

    @Test
    void cleanupShouldExpireSuspendedRetryResumingAndRemoveCompleted() {
        when(runtimeCheckpointMapper.update(any(RuntimeCheckpoint.class), any(Wrapper.class))).thenReturn(2, 3);
        when(runtimeCheckpointMapper.delete(any(Wrapper.class))).thenReturn(4);
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(1);

        int expired = runtimeCheckpointService.markSuspendedExpired(cutoff);
        int retried = runtimeCheckpointService.retryStaleResuming(cutoff);
        int removed = runtimeCheckpointService.removeCompletedBefore(cutoff);

        ArgumentCaptor<RuntimeCheckpoint> captor = ArgumentCaptor.forClass(RuntimeCheckpoint.class);
        verify(runtimeCheckpointMapper, org.mockito.Mockito.times(2)).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getAllValues()).extracting(RuntimeCheckpoint::getCheckpointStatus)
                .containsExactly("expired", "approved");
        assertThat(expired).isEqualTo(2);
        assertThat(retried).isEqualTo(3);
        assertThat(removed).isEqualTo(4);
    }
}
