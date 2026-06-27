package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.entity.AgentPublishScope;
import com.xiaoai.agent.agent.mapper.AgentPublishScopeMapper;
import com.xiaoai.agent.agent.model.PublishAgentCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentPublishScopeService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentPublishScopeServiceImpl extends ServiceImpl<AgentPublishScopeMapper, AgentPublishScope> implements AgentPublishScopeService {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;

    public AgentPublishScopeServiceImpl(AgentService agentService, AgentVersionService agentVersionService) {
        this.agentService = agentService;
        this.agentVersionService = agentVersionService;
    }

    @Override
public AgentPublishScope getPublishScope(Long scopeId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentPublishScope scope = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentPublishScope>()
                .eq(AgentPublishScope::getTenantId, tenantId)
                .eq(AgentPublishScope::getId, scopeId)
                .last("limit 1"));
        if (scope == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent publish scope not found");
        }
        return scope;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public AgentPublishScope publish(Long agentId, PublishAgentCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        Agent agent = agentService.getAgent(agentId);
        agentVersionService.publishVersion(agentId, command.getAgentVersionId());
        AgentPublishScope scope = new AgentPublishScope();
        scope.setTenantId(tenantId);
        scope.setAgentId(agentId);
        scope.setAgentVersionId(command.getAgentVersionId());
        scope.setScopeType(command.getScopeType());
        scope.setScopeValue(command.getScopeValue());
        scope.setStatus("active");
        save(scope);

        agent.setStatus("published");
        agent.setCurrentVersionId(command.getAgentVersionId());
        agent.setLatestStableVersionId(command.getAgentVersionId());
        agentService.updateById(agent);
        return scope;
    }
}
