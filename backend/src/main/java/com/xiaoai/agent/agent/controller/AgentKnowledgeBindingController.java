package com.xiaoai.agent.agent.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.agent.entity.AgentKnowledgeBinding;
import com.xiaoai.agent.agent.service.AgentKnowledgeBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent-knowledge-bindings")
public class AgentKnowledgeBindingController {

    private final AgentKnowledgeBindingService agentKnowledgeBindingService;

    @GetMapping("/{id}")
public ApiResponse<AgentKnowledgeBinding> getById(@PathVariable Long id) {
        return ApiResponse.success(agentKnowledgeBindingService.getBinding(id));
    }
}
