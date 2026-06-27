package com.xiaoai.agent.collaboration.listener;

import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.task.event.TaskStatusChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CollaborationTaskStatusChangedListenerTest {

    private final AgentThreadService agentThreadService = mock(AgentThreadService.class);
    private final CollaborationTaskStatusChangedListener listener = new CollaborationTaskStatusChangedListener(agentThreadService);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void onTaskStatusChangedShouldBindEventContextAndSyncCollaborationThread() {
        Mockito.doAnswer(invocation -> {
            assertThat(UserContextHolder.requireTenantId()).isEqualTo(100L);
            assertThat(UserContextHolder.requireUserId()).isEqualTo(200L);
            return null;
        }).when(agentThreadService).syncThreadByTaskId(99L);

        listener.onTaskStatusChanged(TaskStatusChangedEvent.builder()
                .tenantId(100L)
                .userId(200L)
                .taskId(99L)
                .runId(88L)
                .status("completed")
                .source("runtime_completed")
                .build());

        verify(agentThreadService).syncThreadByTaskId(99L);
    }
}
