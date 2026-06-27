package com.xiaoai.agent.task.listener;

import com.xiaoai.agent.approval.event.ApprovalHandledEvent;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.event.TaskStatusChangedEvent;
import com.xiaoai.agent.task.model.ResumeTaskCommand;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.task.service.TaskRunService;
import com.xiaoai.agent.task.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class ApprovalHandledListener {

    private final TaskService taskService;
    private final TaskRunService taskRunService;
    private final TaskEventRecordService taskEventRecordService;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalHandledListener(TaskService taskService,
                                   TaskRunService taskRunService,
                                   TaskEventRecordService taskEventRecordService) {
        this(taskService, taskRunService, taskEventRecordService, null);
    }

    @Autowired
    public ApprovalHandledListener(TaskService taskService,
                                   TaskRunService taskRunService,
                                   TaskEventRecordService taskEventRecordService,
                                   ApplicationEventPublisher eventPublisher) {
        this.taskService = taskService;
        this.taskRunService = taskRunService;
        this.taskEventRecordService = taskEventRecordService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
public void onApprovalHandled(ApprovalHandledEvent event) {
        UserContext previousContext = UserContextHolder.get();
        bindEventContext(event);
        try {
            if (event.getTaskId() == null) {
                return;
            }
            if ("approve".equals(event.getAction())) {
                resumeApprovedTask(event);
                return;
            }
            if (!"reject".equals(event.getAction())) {
                return;
            }
            Task task = getTaskIfPresent(event.getTaskId());
            if (task == null) {
                return;
            }
            task.setStatus("failed");
            task.setResultSummary("Approval rejected: " + safeText(event.getCommentText()));
            taskService.updateById(task);

            TaskRun run = event.getRunId() == null ? null : getRunIfPresent(event.getRunId());
            if (run != null) {
                run.setStatus("failed");
                run.setEndTime(OffsetDateTime.now());
                run.setFailReason("Approval rejected: " + safeText(event.getCommentText()));
                taskRunService.updateById(run);
            }
            publishTaskStatusChanged(task, run, event);

            taskEventRecordService.record(RuntimeEvent.builder()
                    .tenantId(event.getTenantId())
                    .taskId(event.getTaskId())
                    .runId(event.getRunId())
                    .eventType("APPROVAL_REJECTED")
                    .eventSummary("Approval rejected")
                    .payloadJson("{\"approvalRequestId\":" + event.getApprovalRequestId()
                            + ",\"comment\":\"" + safeJson(event.getCommentText()) + "\"}")
                    .occurredAt(OffsetDateTime.now())
                    .build());
            taskEventRecordService.record(RuntimeEvent.builder()
                    .tenantId(event.getTenantId())
                    .taskId(event.getTaskId())
                    .runId(event.getRunId())
                    .eventType("RUN_FAILED")
                    .eventSummary("Run failed because approval was rejected")
                    .payloadJson("{\"approvalRequestId\":" + event.getApprovalRequestId() + "}")
                    .occurredAt(OffsetDateTime.now())
                    .build());
        } finally {
            restoreContext(previousContext);
        }
    }

    private void resumeApprovedTask(ApprovalHandledEvent event) {
        Task task = getTaskIfPresent(event.getTaskId());
        if (task == null) {
            return;
        }
        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(event.getTenantId())
                .taskId(event.getTaskId())
                .runId(event.getRunId())
                .eventType("APPROVAL_APPROVED")
                .eventSummary("Approval approved")
                .payloadJson("{\"approvalRequestId\":" + event.getApprovalRequestId()
                        + ",\"comment\":\"" + safeJson(event.getCommentText()) + "\"}")
                .occurredAt(OffsetDateTime.now())
                .build());
        ResumeTaskCommand command = new ResumeTaskCommand();
        command.setApprovalRequestId(event.getApprovalRequestId());
        command.setResumePayloadJson("{\"approvalRequestId\":" + event.getApprovalRequestId() + "}");
        taskService.resumeTask(event.getTaskId(), command);
    }

    private void bindEventContext(ApprovalHandledEvent event) {
        if (event.getTenantId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing tenant context");
        }
        UserContextHolder.set(UserContext.builder()
                .tenantId(event.getTenantId())
                .userId(event.getOperatorUserId())
                .build());
    }

    private void restoreContext(UserContext previousContext) {
        if (previousContext == null) {
            UserContextHolder.clear();
            return;
        }
        UserContextHolder.set(previousContext);
    }

    private Task getTaskIfPresent(Long taskId) {
        try {
            return taskService.getTask(taskId);
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND.getCode().equals(exception.getCode())) {
                return null;
            }
            throw exception;
        }
    }

    private TaskRun getRunIfPresent(Long runId) {
        try {
            return taskRunService.getRun(runId);
        } catch (BusinessException exception) {
            if (ErrorCode.NOT_FOUND.getCode().equals(exception.getCode())) {
                return null;
            }
            throw exception;
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void publishTaskStatusChanged(Task task, TaskRun run, ApprovalHandledEvent event) {
        if (eventPublisher == null || task == null) {
            return;
        }
        eventPublisher.publishEvent(TaskStatusChangedEvent.builder()
                .tenantId(event.getTenantId())
                .userId(task.getUserId())
                .taskId(task.getId())
                .runId(run == null ? event.getRunId() : run.getId())
                .status(task.getStatus())
                .source("approval_rejected")
                .build());
    }
}
