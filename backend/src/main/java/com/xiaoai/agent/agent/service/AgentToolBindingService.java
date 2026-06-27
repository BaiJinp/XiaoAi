package com.xiaoai.agent.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.agent.entity.AgentToolBinding;

import java.util.List;

public interface AgentToolBindingService extends IService<AgentToolBinding> {

    AgentToolBinding getBinding(Long bindingId);

    List<AgentToolBinding> listVersionBindings(Long agentVersionId);
}
