package com.xiaoai.agent.tool.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("tool_config")
public class ToolConfig extends TenantEntity {
    private String toolCode;

    private String toolName;

    private String toolType;

    private String riskLevel;

    private String endpointUrl;

    private String schemaJson;

    private String authConfigJson;

    private String status;
}
