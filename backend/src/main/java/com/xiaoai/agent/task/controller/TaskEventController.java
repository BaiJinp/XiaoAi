package com.xiaoai.agent.task.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.model.TaskEventQuery;
import com.xiaoai.agent.task.service.TaskEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-events")
public class TaskEventController {

    private final TaskEventService taskEventService;

    @GetMapping("/{id}")
public ApiResponse<TaskEvent> getById(@PathVariable Long id) {
        return ApiResponse.success(taskEventService.getEvent(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskEvent>> pageEvents(TaskEventQuery query) {
        return ApiResponse.success(taskEventService.pageEvents(query));
    }
}
