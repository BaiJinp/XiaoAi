package com.xiaoai.agent.task.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.service.TaskArtifactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-artifacts")
public class TaskArtifactController {

    private final TaskArtifactService taskArtifactService;

    @GetMapping("/{id}")
public ApiResponse<TaskArtifact> getById(@PathVariable Long id) {
        return ApiResponse.success(taskArtifactService.getArtifact(id));
    }
}
