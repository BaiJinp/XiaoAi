package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.model.UpdateAgentRoleDefaultAgentCommand;

import java.util.List;

public interface AgentRoleService extends IService<AgentRole> {

    AgentRole getRole(Long roleId);

    List<AgentRole> listActiveRoles(String domainCode);

    AgentRole updateDefaultAgent(Long roleId, UpdateAgentRoleDefaultAgentCommand command);
}
