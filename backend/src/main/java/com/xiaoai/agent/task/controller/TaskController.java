package com.xiaoai.agent.task.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.model.CancelTaskCommand;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.SuspendForApprovalCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskPageQuery;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.model.ResumeTaskCommand;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool();

    private final TaskService taskService;
    private final TaskArtifactService taskArtifactService;

    @GetMapping("/{id}")
public ApiResponse<Task> getById(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTask(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<Task>> pageTasks(TaskPageQuery query) {
        return ApiResponse.success(taskService.pageTasks(query));
    }

    @PostMapping
public ApiResponse<TaskCreateResponse> createTask(@Valid @RequestBody CreateTaskCommand command) {
        return ApiResponse.success(taskService.createTask(command));
    }

    @PostMapping("/{id}/runs")
public ApiResponse<TaskRunResponse> startTask(
            @PathVariable Long id,
            @RequestBody StartTaskCommand command) {
        return ApiResponse.success(taskService.startTask(id, command));
    }

    @PostMapping("/{id}/cancel")
public ApiResponse<Void> cancelTask(
            @PathVariable Long id,
            @RequestBody CancelTaskCommand command) {
        taskService.cancelTask(id, command);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/suspend-for-approval")
public ApiResponse<Void> suspendForApproval(
            @PathVariable Long id,
            @Valid @RequestBody SuspendForApprovalCommand command) {
        taskService.suspendForApproval(id, command);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/resume")
public ApiResponse<Void> resumeTask(
            @PathVariable Long id,
            @Valid @RequestBody ResumeTaskCommand command) {
        taskService.resumeTask(id, command);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<TaskEvent>> listTaskEvents(@PathVariable Long id,
                                                       @RequestParam(required = false) Long lastEventId) {
        return ApiResponse.success(taskService.listTaskEventsAfter(id, lastEventId));
    }

    @GetMapping("/{id}/artifacts")
    public ApiResponse<List<TaskArtifact>> listTaskArtifacts(@PathVariable Long id) {
        return ApiResponse.success(taskArtifactService.listByTaskId(id));
    }

    @GetMapping("/{id}/events/stream")
    public SseEmitter streamTaskEvents(@PathVariable Long id,
                                       @RequestParam(required = false) Long lastEventId,
                                       @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventIdHeader) {
        SseEmitter emitter = new SseEmitter(30_000L);
        UserContext userContext = UserContextHolder.get();
        Long resumeAfterEventId = lastEventId != null ? lastEventId : lastEventIdHeader;
        SSE_EXECUTOR.execute(() -> {
            try {
                UserContextHolder.set(userContext);
                for (TaskEvent event : taskService.listTaskEventsAfter(id, resumeAfterEventId)) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.getId()))
                            .name(event.getEventType())
                            .data(event));
                }
                emitter.complete();
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            } finally {
                UserContextHolder.clear();
            }
        });
        return emitter;
    }
}
