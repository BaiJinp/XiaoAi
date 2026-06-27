package com.xiaoai.agent.task.service.impl;

import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.service.TaskEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskEventRecordServiceImplTest {

    private final TaskEventService taskEventService = mock(TaskEventService.class);
    private final TaskEventRecordServiceImpl taskEventRecordService = new TaskEventRecordServiceImpl(taskEventService);

    @Test
    void recordShouldMapRuntimeEventTypesToActionableLevels() {
        record("TOOL_FAILED");
        record("RUN_FAILED");
        record("TOOL_BLOCKED");
        record("TOOL_DENIED");
        record("APPROVAL_REJECTED");
        record("TOOL_RESULT");

        ArgumentCaptor<TaskEvent> captor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventService, org.mockito.Mockito.times(6)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TaskEvent::getEventLevel)
                .containsExactly("error", "error", "warn", "warn", "warn", "info");
    }

    private void record(String eventType) {
        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(2L)
                .eventType(eventType)
                .eventSummary(eventType)
                .payloadJson("{}")
                .build());
    }
}
