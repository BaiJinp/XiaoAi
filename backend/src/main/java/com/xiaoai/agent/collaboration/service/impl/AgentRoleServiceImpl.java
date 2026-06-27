package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.mapper.AgentRoleMapper;
import com.xiaoai.agent.collaboration.model.UpdateAgentRoleDefaultAgentCommand;
import com.xiaoai.agent.collaboration.service.AgentRoleService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AgentRoleServiceImpl extends ServiceImpl<AgentRoleMapper, AgentRole> implements AgentRoleService {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;

    public AgentRoleServiceImpl() {
        this(null, null);
    }

    @Autowired
    public AgentRoleServiceImpl(AgentService agentService, AgentVersionService agentVersionService) {
        this.agentService = agentService;
        this.agentVersionService = agentVersionService;
    }

    @Override
public AgentRole getRole(Long roleId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentRole role = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentRole>()
                .eq(AgentRole::getTenantId, tenantId)
                .eq(AgentRole::getId, roleId)
                .last("limit 1"));
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent role not found");
        }
        return role;
    }

    @Override
public List<AgentRole> listActiveRoles(String domainCode) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<AgentRole> query = new LambdaQueryWrapper<AgentRole>()
                .eq(AgentRole::getTenantId, tenantId)
                .eq(AgentRole::getStatus, "active")
                .eq(StringUtils.hasText(domainCode), AgentRole::getDomainCode, domainCode)
                .orderByAsc(AgentRole::getDomainCode)
                .orderByAsc(AgentRole::getRoleCode);
        return getBaseMapper().selectList(query);
    }

    @Override
public AgentRole updateDefaultAgent(Long roleId, UpdateAgentRoleDefaultAgentCommand command) {
        AgentRole role = getRole(roleId);
        Long defaultAgentId = command == null ? null : command.getDefaultAgentId();
        Long defaultAgentVersionId = command == null ? null : command.getDefaultAgentVersionId();
        if ((defaultAgentId == null) != (defaultAgentVersionId == null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Default agent and version must be set together");
        }

        if (defaultAgentId != null) {
            if (agentService == null || agentVersionService == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent role binding validator is not available");
            }
            agentService.getAgent(defaultAgentId);
            AgentVersion version = agentVersionService.getVersion(defaultAgentVersionId);
            if (!defaultAgentId.equals(version.getAgentId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent version does not belong to default agent");
            }
        }

        update(new UpdateWrapper<AgentRole>()
                .eq("tenant_id", role.getTenantId())
                .eq("id", role.getId())
                .set("default_agent_id", defaultAgentId)
                .set("default_agent_version_id", defaultAgentVersionId));
        role.setDefaultAgentId(defaultAgentId);
        role.setDefaultAgentVersionId(defaultAgentVersionId);
        return role;
    }
}
