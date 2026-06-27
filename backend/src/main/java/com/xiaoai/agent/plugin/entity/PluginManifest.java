package com.xiaoai.agent.plugin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("plugin_manifest")
public class PluginManifest extends TenantEntity {
    private String pluginCode;

    private String pluginName;

    private String pluginVersion;

    private String manifestJson;

    private String status;
}
