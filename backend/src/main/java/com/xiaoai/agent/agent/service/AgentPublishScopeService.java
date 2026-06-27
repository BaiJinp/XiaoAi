package com.xiaoai.agent.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.agent.entity.AgentPublishScope;
import com.xiaoai.agent.agent.model.PublishAgentCommand;

public interface AgentPublishScopeService extends IService<AgentPublishScope> {

    AgentPublishScope getPublishScope(Long scopeId);

    AgentPublishScope publish(Long agentId, PublishAgentCommand command);
}
