package com.xiaoai.agent.task.controller;

import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
    private final TaskController taskController = new TaskController(taskService, taskArtifactService);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void streamTaskEventsShouldPropagateUserContextToAsyncWorker() throws Exception {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-1")
                .build());
        AtomicReference<Long> tenantIdInWorker = new AtomicReference<>();
        TaskEvent event = new TaskEvent();
        event.setTenantId(100L);
        event.setTaskId(10L);
        event.setRunId(20L);
        event.setEventType("RUN_STARTED");
        event.setEventSummary("Runtime run started");
        event.setOccurredAt(OffsetDateTime.now());
        when(taskService.listTaskEventsAfter(10L, null)).thenAnswer(invocation -> {
            tenantIdInWorker.set(UserContextHolder.requireTenantId());
            return List.of(event);
        });

        SseEmitter emitter = taskController.streamTaskEvents(10L, null, null);
        Thread.sleep(200L);

        assertThat(emitter).isNotNull();
        assertThat(tenantIdInWorker).hasValue(100L);
    }

    @Test
    void streamTaskEventsShouldUseLastEventIdQueryBeforeHeader() throws Exception {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-1")
                .build());
        when(taskService.listTaskEventsAfter(10L, 3L)).thenReturn(List.of());

        SseEmitter emitter = taskController.streamTaskEvents(10L, 3L, 2L);
        Thread.sleep(200L);

        assertThat(emitter).isNotNull();
        org.mockito.Mockito.verify(taskService).listTaskEventsAfter(10L, 3L);
    }
}
