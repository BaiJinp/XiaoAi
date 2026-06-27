package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.agent.entity.AgentToolBinding;
import com.xiaoai.agent.agent.mapper.AgentToolBindingMapper;
import com.xiaoai.agent.agent.service.AgentToolBindingService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentToolBindingServiceImpl extends ServiceImpl<AgentToolBindingMapper, AgentToolBinding> implements AgentToolBindingService {

    @Override
public AgentToolBinding getBinding(Long bindingId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentToolBinding binding = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentToolBinding>()
                .eq(AgentToolBinding::getTenantId, tenantId)
                .eq(AgentToolBinding::getId, bindingId)
                .last("limit 1"));
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent tool binding not found");
        }
        return binding;
    }

    @Override
public List<AgentToolBinding> listVersionBindings(Long agentVersionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return getBaseMapper().selectList(new LambdaQueryWrapper<AgentToolBinding>()
                .eq(AgentToolBinding::getTenantId, tenantId)
                .eq(AgentToolBinding::getAgentVersionId, agentVersionId)
                .orderByAsc(AgentToolBinding::getId));
    }
}
