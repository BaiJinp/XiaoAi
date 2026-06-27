package com.xiaoai.agent.task.listener;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.approval.event.ApprovalHandledEvent;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.event.TaskStatusChangedEvent;
import com.xiaoai.agent.task.model.ResumeTaskCommand;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.task.service.TaskRunService;
import com.xiaoai.agent.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalHandledListenerTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskRunService taskRunService = mock(TaskRunService.class);
    private final TaskEventRecordService taskEventRecordService = mock(TaskEventRecordService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ApprovalHandledListener listener = new ApprovalHandledListener(
            taskService,
            taskRunService,
            taskEventRecordService,
            eventPublisher
    );

    @Test
    void rejectedApprovalShouldFailTaskRunAndRecordEvents() {
        Task task = new Task();
        task.setId(1L);
        task.setStatus("suspended");
        TaskRun run = new TaskRun();
        run.setId(99L);
        run.setStatus("suspended");
        when(taskService.getTask(1L)).thenReturn(task);
        when(taskRunService.getRun(99L)).thenReturn(run);

        listener.onApprovalHandled(ApprovalHandledEvent.builder()
                .tenantId(100L)
                .approvalRequestId(88L)
                .taskId(1L)
                .runId(99L)
                .operatorUserId(300L)
                .action("reject")
                .commentText("风险过高")
                .build());

        assertThat(task.getStatus()).isEqualTo("failed");
        assertThat(task.getResultSummary()).contains("风险过高");
        assertThat(run.getStatus()).isEqualTo("failed");
        assertThat(run.getFailReason()).contains("风险过高");
        assertThat(run.getEndTime()).isNotNull();
        verify(taskService).updateById(task);
        verify(taskRunService).updateById(run);
        ArgumentCaptor<RuntimeEvent> captor = ArgumentCaptor.forClass(RuntimeEvent.class);
        verify(taskEventRecordService, times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(RuntimeEvent::getEventType)
                .isEqualTo(List.of("APPROVAL_REJECTED", "RUN_FAILED"));
        ArgumentCaptor<TaskStatusChangedEvent> statusCaptor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getTaskId()).isEqualTo(1L);
        assertThat(statusCaptor.getValue().getRunId()).isEqualTo(99L);
        assertThat(statusCaptor.getValue().getStatus()).isEqualTo("failed");
        assertThat(statusCaptor.getValue().getSource()).isEqualTo("approval_rejected");
    }

    @Test
    void approvedApprovalShouldRecordEventAndResumeTask() {
        Task task = new Task();
        task.setId(1L);
        task.setStatus("suspended");
        when(taskService.getTask(1L)).thenReturn(task);

        listener.onApprovalHandled(ApprovalHandledEvent.builder()
                .tenantId(100L)
                .approvalRequestId(88L)
                .taskId(1L)
                .runId(99L)
                .action("approve")
                .commentText("同意")
                .build());

        ArgumentCaptor<RuntimeEvent> eventCaptor = ArgumentCaptor.forClass(RuntimeEvent.class);
        verify(taskEventRecordService).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("APPROVAL_APPROVED");
        ArgumentCaptor<ResumeTaskCommand> commandCaptor = ArgumentCaptor.forClass(ResumeTaskCommand.class);
        verify(taskService).resumeTask(org.mockito.ArgumentMatchers.eq(1L), commandCaptor.capture());
        assertThat(commandCaptor.getValue().getApprovalRequestId()).isEqualTo(88L);
        verify(taskRunService, times(0)).updateById(any());
    }

    @Test
    void missingTaskShouldIgnoreApprovalEvent() {
        when(taskService.getTask(1L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Task not found"));

        listener.onApprovalHandled(ApprovalHandledEvent.builder()
                .tenantId(100L)
                .approvalRequestId(88L)
                .taskId(1L)
                .runId(99L)
                .action("reject")
                .commentText("拒绝")
                .build());

        verify(taskService, never()).updateById(any());
        verify(taskRunService, never()).updateById(any());
        verify(taskEventRecordService, never()).record(any());
    }
}
