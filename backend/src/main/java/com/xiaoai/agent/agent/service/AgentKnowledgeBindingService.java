package com.xiaoai.agent.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.agent.entity.AgentKnowledgeBinding;

public interface AgentKnowledgeBindingService extends IService<AgentKnowledgeBinding> {

    AgentKnowledgeBinding getBinding(Long bindingId);
}
