package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.TaskRunResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration-sessions/{sessionId}/threads")
public class AgentThreadController {

    private final AgentThreadService agentThreadService;

    @GetMapping
    public ApiResponse<List<AgentThread>> listThreads(@PathVariable Long sessionId) {
        return ApiResponse.success(agentThreadService.listThreads(sessionId));
    }

    @PostMapping
public ApiResponse<AgentThreadResponse> createThread(@PathVariable Long sessionId,
                                                         @Valid @RequestBody CreateAgentThreadCommand command) {
        command.setSessionId(sessionId);
        return ApiResponse.success(agentThreadService.createThread(command));
    }

    @PostMapping("/{threadId}/start")
    public ApiResponse<TaskRunResponse> startThreadTask(@PathVariable Long sessionId,
                                                        @PathVariable Long threadId,
                                                        @RequestBody(required = false) StartTaskCommand command) {
        return ApiResponse.success(agentThreadService.startThreadTask(sessionId, threadId, command));
    }
}
