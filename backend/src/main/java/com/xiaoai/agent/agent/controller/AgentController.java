package com.xiaoai.agent.agent.controller;

import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.model.AgentDraftResponse;
import com.xiaoai.agent.agent.model.AgentPageQuery;
import com.xiaoai.agent.agent.model.CreateAgentDraftCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
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
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/{id}")
public ApiResponse<Agent> getById(@PathVariable Long id) {
        return ApiResponse.success(agentService.getAgent(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<Agent>> pageAgents(AgentPageQuery query) {
        return ApiResponse.success(agentService.pageAgents(query));
    }

    @PostMapping("/drafts")
public ApiResponse<AgentDraftResponse> createDraft(@Valid @RequestBody CreateAgentDraftCommand command) {
        return ApiResponse.success(agentService.createDraft(command));
    }
}
