package com.xiaoai.agent.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.mapper.ModelProviderMapper;
import com.xiaoai.agent.model.model.CreateModelProviderCommand;
import com.xiaoai.agent.model.service.ModelProviderService;
import org.springframework.stereotype.Service;

@Service
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider> implements ModelProviderService {

    @Override
public ModelProvider createModelProvider(CreateModelProviderCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        ModelProvider provider = new ModelProvider();
        provider.setTenantId(tenantId);
        provider.setProviderCode(command.getProviderCode());
        provider.setProviderName(command.getProviderName());
        provider.setProviderType(command.getProviderType());
        provider.setBaseUrl(command.getBaseUrl());
        provider.setAuthConfigJson("{\"apiKey\":\"" + escapeJson(command.getApiKey()) + "\"}");
        provider.setStatus("active");
        save(provider);
        return provider;
    }

    @Override
public ModelProvider getModelProvider(Long providerId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ModelProvider provider = getBaseMapper().selectOne(new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getTenantId, tenantId)
                .eq(ModelProvider::getId, providerId)
                .last("limit 1"));
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Model provider not found");
        }
        return provider;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
