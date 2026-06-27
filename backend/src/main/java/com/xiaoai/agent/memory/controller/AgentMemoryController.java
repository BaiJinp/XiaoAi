package com.xiaoai.agent.memory.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.model.AgentMemoryPageQuery;
import com.xiaoai.agent.memory.model.CreateAgentMemoryCommand;
import com.xiaoai.agent.memory.service.AgentMemoryService;
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
@RequestMapping("/api/v1/agent-memories")
public class AgentMemoryController {

    private final AgentMemoryService agentMemoryService;

    @GetMapping
    public ApiResponse<PageResponse<AgentMemory>> pageMemories(AgentMemoryPageQuery query) {
        return ApiResponse.success(agentMemoryService.pageMemories(query));
    }

    @PostMapping
public ApiResponse<AgentMemory> createConfirmedMemory(@Valid @RequestBody CreateAgentMemoryCommand command) {
        return ApiResponse.success(agentMemoryService.createConfirmedMemory(command));
    }

    @PostMapping("/{id}/archive")
public ApiResponse<AgentMemory> archiveMemory(@PathVariable Long id) {
        return ApiResponse.success(agentMemoryService.archiveMemory(id));
    }
}
