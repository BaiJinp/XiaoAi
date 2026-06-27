package com.xiaoai.agent.agent.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent-versions")
public class AgentVersionController {

    private final AgentVersionService agentVersionService;

    @GetMapping("/{id}")
public ApiResponse<AgentVersion> getById(@PathVariable Long id) {
        return ApiResponse.success(agentVersionService.getVersion(id));
    }

    @GetMapping("/agents/{agentId}/versions")
    public ApiResponse<List<AgentVersionResponse>> listVersionsByAgent(@PathVariable Long agentId) {
        return ApiResponse.success(agentVersionService.listVersionsByAgent(agentId));
    }

    @PostMapping("/agents/{agentId}/versions")
public ApiResponse<AgentVersionResponse> createVersion(
            @PathVariable Long agentId,
            @Valid @RequestBody UpdateAgentConfigCommand command) {
        return ApiResponse.success(agentVersionService.createVersion(agentId, command));
    }

    @PutMapping("/agents/{agentId}/versions/{versionId}/tools")
public ApiResponse<AgentVersionResponse> replaceVersionTools(
            @PathVariable Long agentId,
            @PathVariable Long versionId,
            @Valid @RequestBody UpdateAgentConfigCommand command) {
        return ApiResponse.success(agentVersionService.replaceVersionTools(agentId, versionId, command));
    }
}
