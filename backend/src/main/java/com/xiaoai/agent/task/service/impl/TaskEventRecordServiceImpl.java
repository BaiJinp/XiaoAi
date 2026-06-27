package com.xiaoai.agent.task.service.impl;

import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.task.service.TaskEventService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class TaskEventRecordServiceImpl implements TaskEventRecordService {

    private final TaskEventService taskEventService;

    public TaskEventRecordServiceImpl(TaskEventService taskEventService) {
        this.taskEventService = taskEventService;
    }

    @Override
public void record(RuntimeEvent event) {
        TaskEvent taskEvent = new TaskEvent();
        taskEvent.setTenantId(event.getTenantId());
        taskEvent.setTaskId(event.getTaskId());
        taskEvent.setRunId(event.getRunId());
        taskEvent.setStepId(event.getStepId());
        taskEvent.setEventType(event.getEventType());
        taskEvent.setEventLevel(resolveEventLevel(event.getEventType()));
        taskEvent.setEventSummary(event.getEventSummary());
        taskEvent.setPayloadJson(event.getPayloadJson() == null ? "{}" : event.getPayloadJson());
        taskEvent.setTraceId(event.getTraceId());
        taskEvent.setOccurredAt(event.getOccurredAt() == null ? OffsetDateTime.now() : event.getOccurredAt());
        taskEventService.save(taskEvent);
    }

    private String resolveEventLevel(String eventType) {
        if (eventType == null) {
            return "info";
        }
        return switch (eventType) {
            case "RUN_FAILED", "TOOL_FAILED" -> "error";
            case "APPROVAL_REJECTED", "TOOL_BLOCKED", "TOOL_DENIED", "ASSISTANT_TASK_SUSPENDED" -> "warn";
            default -> "info";
        };
    }
}
