package com.xiaoai.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("model_config")
public class ModelConfig extends TenantEntity {
    private Long providerId;

    private String modelCode;

    private String modelName;

    private String modelType;

    private Integer contextWindow;

    private String configJson;

    private String status;
}
