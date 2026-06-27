package com.xiaoai.agent.task.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.task.entity.TaskStep;
import com.xiaoai.agent.task.service.TaskStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-steps")
public class TaskStepController {

    private final TaskStepService taskStepService;

    @GetMapping("/{id}")
public ApiResponse<TaskStep> getById(@PathVariable Long id) {
        return ApiResponse.success(taskStepService.getStep(id));
    }
}
