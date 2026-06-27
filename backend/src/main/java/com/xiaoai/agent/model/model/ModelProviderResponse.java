package com.xiaoai.agent.model.model;

import com.xiaoai.agent.model.entity.ModelProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModelProviderResponse {

    private final Long id;

    private final String providerCode;

    private final String providerName;

    private final String providerType;

    private final String baseUrl;

    private final String status;

    public static ModelProviderResponse from(ModelProvider provider) {
        return ModelProviderResponse.builder()
                .id(provider.getId())
                .providerCode(provider.getProviderCode())
                .providerName(provider.getProviderName())
                .providerType(provider.getProviderType())
                .baseUrl(provider.getBaseUrl())
                .status(provider.getStatus())
                .build();
    }

}
