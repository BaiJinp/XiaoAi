package com.xiaoai.agent.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.model.entity.ModelProvider;

public interface ModelProviderService extends IService<ModelProvider> {

    ModelProvider createModelProvider(com.xiaoai.agent.model.model.CreateModelProviderCommand command);

    ModelProvider getModelProvider(Long providerId);
}
