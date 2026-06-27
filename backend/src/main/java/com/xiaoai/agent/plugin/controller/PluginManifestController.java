package com.xiaoai.agent.plugin.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.plugin.model.ImportPluginManifestCommand;
import com.xiaoai.agent.plugin.model.PluginManifestResponse;
import com.xiaoai.agent.plugin.service.PluginManifestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plugin-manifests")
public class PluginManifestController {

    private final PluginManifestService pluginManifestService;

    @PostMapping("/import")
public ApiResponse<PluginManifestResponse> importManifest(@Valid @RequestBody ImportPluginManifestCommand command) {
        return ApiResponse.success(pluginManifestService.importManifest(command));
    }

    @GetMapping
    public ApiResponse<List<PluginManifestResponse>> listManifests() {
        return ApiResponse.success(pluginManifestService.listManifests());
    }

    @PostMapping("/{pluginId}/enable")
public ApiResponse<PluginManifestResponse> enablePlugin(@PathVariable Long pluginId) {
        return ApiResponse.success(pluginManifestService.enablePlugin(pluginId));
    }

    @PostMapping("/{pluginId}/disable")
public ApiResponse<PluginManifestResponse> disablePlugin(@PathVariable Long pluginId) {
        return ApiResponse.success(pluginManifestService.disablePlugin(pluginId));
    }
}
