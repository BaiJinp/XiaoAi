package com.xiaoai.agent.memory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.model.AgentMemoryPageQuery;
import com.xiaoai.agent.memory.model.CreateAgentMemoryCommand;

import java.util.List;

public interface AgentMemoryService extends IService<AgentMemory> {

    AgentMemory createConfirmedMemory(CreateAgentMemoryCommand command);

    PageResponse<AgentMemory> pageMemories(AgentMemoryPageQuery query);

    List<AgentMemory> listConfirmedMemoriesForRuntime(Long tenantId,
                                                       Long agentId,
                                                       Long taskId,
                                                       Long sessionId,
                                                       Long userId,
                                                       int limit);

    List<AgentMemory> listConfirmedMemoriesForRuntime(Long tenantId,
                                                       Long agentId,
                                                       Long taskId,
                                                       Long sessionId,
                                                       Long userId,
                                                       List<String> scopes,
                                                       int limit);

    AgentMemory archiveMemory(Long memoryId);
}
