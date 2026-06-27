package com.xiaoai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("model_provider")
public class ModelProvider extends TenantEntity {
    private String providerCode;

    private String providerName;

    private String providerType;

    private String baseUrl;

    private String authConfigJson;

    private String status;
}
