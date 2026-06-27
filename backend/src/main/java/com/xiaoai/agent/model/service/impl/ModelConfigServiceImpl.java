package com.xiaoai.agent.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.mapper.ModelConfigMapper;
import com.xiaoai.agent.model.model.CreateModelConfigCommand;
import com.xiaoai.agent.model.service.ModelConfigService;
import org.springframework.stereotype.Service;

@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Override
public ModelConfig createModelConfig(CreateModelConfigCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        ModelConfig model = new ModelConfig();
        model.setTenantId(tenantId);
        model.setProviderId(command.getProviderId());
        model.setModelCode(command.getModelCode());
        model.setModelName(command.getModelName());
        model.setModelType(command.getModelType());
        model.setContextWindow(command.getContextWindow());
        model.setConfigJson(command.getConfigJson() == null || command.getConfigJson().isBlank() ? "{}" : command.getConfigJson());
        model.setStatus("active");
        save(model);
        return model;
    }

    @Override
public ModelConfig getModelConfig(Long modelId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ModelConfig model = getBaseMapper().selectOne(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getTenantId, tenantId)
                .eq(ModelConfig::getId, modelId)
                .last("limit 1"));
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Model config not found");
        }
        return model;
    }
}
