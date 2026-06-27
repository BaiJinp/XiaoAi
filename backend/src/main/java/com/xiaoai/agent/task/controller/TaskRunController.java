package com.xiaoai.agent.task.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.service.TaskRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-runs")
public class TaskRunController {

    private final TaskRunService taskRunService;

    @GetMapping("/{id}")
public ApiResponse<TaskRun> getById(@PathVariable Long id) {
        return ApiResponse.success(taskRunService.getRun(id));
    }
}
