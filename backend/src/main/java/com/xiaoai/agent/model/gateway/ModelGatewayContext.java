package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModelGatewayContext {

    private final ModelConfig model;

    private final ModelProvider provider;
}
