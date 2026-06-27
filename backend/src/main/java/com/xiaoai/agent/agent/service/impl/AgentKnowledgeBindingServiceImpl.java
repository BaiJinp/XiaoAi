package com.xiaoai.agent.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.agent.entity.AgentKnowledgeBinding;
import com.xiaoai.agent.agent.mapper.AgentKnowledgeBindingMapper;
import com.xiaoai.agent.agent.service.AgentKnowledgeBindingService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class AgentKnowledgeBindingServiceImpl extends ServiceImpl<AgentKnowledgeBindingMapper, AgentKnowledgeBinding> implements AgentKnowledgeBindingService {

    @Override
public AgentKnowledgeBinding getBinding(Long bindingId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentKnowledgeBinding binding = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                .eq(AgentKnowledgeBinding::getTenantId, tenantId)
                .eq(AgentKnowledgeBinding::getId, bindingId)
                .last("limit 1"));
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent knowledge binding not found");
        }
        return binding;
    }
}
