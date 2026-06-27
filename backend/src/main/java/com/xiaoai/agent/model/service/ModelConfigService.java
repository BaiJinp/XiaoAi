package com.xiaoai.agent.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.model.entity.ModelConfig;

public interface ModelConfigService extends IService<ModelConfig> {

    ModelConfig createModelConfig(com.xiaoai.agent.model.model.CreateModelConfigCommand command);

    ModelConfig getModelConfig(Long modelId);
}
