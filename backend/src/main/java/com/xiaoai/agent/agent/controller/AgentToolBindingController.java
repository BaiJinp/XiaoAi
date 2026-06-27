package com.xiaoai.agent.agent.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent-tool-bindings")
public class AgentToolBindingController {

    private final AgentToolBindingService agentToolBindingService;

    @GetMapping("/{id}")
public ApiResponse<AgentToolBinding> getById(@PathVariable Long id) {
        return ApiResponse.success(agentToolBindingService.getBinding(id));
    }

    @GetMapping("/agent-versions/{agentVersionId}")
    public ApiResponse<List<AgentToolBinding>> listByAgentVersion(@PathVariable Long agentVersionId) {
        return ApiResponse.success(agentToolBindingService.listVersionBindings(agentVersionId));
    }
}
