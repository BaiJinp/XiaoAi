package com.xiaoai.agent.agent.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.agent.entity.AgentPublishScope;
import com.xiaoai.agent.agent.model.PublishAgentCommand;
import com.xiaoai.agent.agent.service.AgentPublishScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent-publish-scopes")
public class AgentPublishScopeController {

    private final AgentPublishScopeService agentPublishScopeService;

    @GetMapping("/{id}")
public ApiResponse<AgentPublishScope> getById(@PathVariable Long id) {
        return ApiResponse.success(agentPublishScopeService.getPublishScope(id));
    }

    @PostMapping("/agents/{agentId}/publish-scopes")
public ApiResponse<AgentPublishScope> publish(
            @PathVariable Long agentId,
            @Valid @RequestBody PublishAgentCommand command) {
        return ApiResponse.success(agentPublishScopeService.publish(agentId, command));
    }
}
