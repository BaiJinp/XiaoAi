package com.xiaoai.agent.collaboration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("artifact_type")
public class ArtifactType extends TenantEntity {

    private String typeCode;

    private String typeName;

    private String domainCode;

    private String schemaJson;

    private String rendererType;

    private String validatorJson;

    private String status;
}
