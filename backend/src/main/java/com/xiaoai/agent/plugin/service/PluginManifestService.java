package com.xiaoai.agent.plugin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.plugin.entity.PluginManifest;
import com.xiaoai.agent.plugin.model.ImportPluginManifestCommand;
import com.xiaoai.agent.plugin.model.PluginManifestResponse;

import java.util.List;

public interface PluginManifestService extends IService<PluginManifest> {

    PluginManifestResponse importManifest(ImportPluginManifestCommand command);

    List<PluginManifestResponse> listManifests();

    PluginManifestResponse enablePlugin(Long pluginId);

    PluginManifestResponse disablePlugin(Long pluginId);
}
