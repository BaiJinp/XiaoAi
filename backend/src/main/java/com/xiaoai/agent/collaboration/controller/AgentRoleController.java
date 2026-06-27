package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.model.UpdateAgentRoleDefaultAgentCommand;
import com.xiaoai.agent.collaboration.service.AgentRoleService;
import com.xiaoai.agent.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent-roles")
public class AgentRoleController {

    private final AgentRoleService agentRoleService;

    @GetMapping
    public ApiResponse<List<AgentRole>> listRoles(@RequestParam(required = false) String domainCode) {
        return ApiResponse.success(agentRoleService.listActiveRoles(domainCode));
    }

    @PutMapping("/{roleId}/default-agent")
public ApiResponse<AgentRole> updateDefaultAgent(@PathVariable Long roleId,
                                                     @RequestBody UpdateAgentRoleDefaultAgentCommand command) {
        return ApiResponse.success(agentRoleService.updateDefaultAgent(roleId, command));
    }
}
