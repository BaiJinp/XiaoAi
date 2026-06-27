package com.xiaoai.agent.plugin.model;

import com.xiaoai.agent.plugin.entity.PluginManifest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PluginManifestResponse {

    private final Long pluginId;

    private final String pluginCode;

    private final String pluginName;

    private final String pluginVersion;

    private final String status;

    private final String manifestHash;

    private final Long boundAgentVersionId;

    private final List<Long> agentVersionToolIds;

    private final List<PluginToolResponse> tools;

    public static PluginManifestResponse from(PluginManifest plugin, List<PluginToolResponse> tools) {
        return from(plugin, tools, null, List.of());
    }

    public static PluginManifestResponse from(PluginManifest plugin,
                                              List<PluginToolResponse> tools,
                                              Long boundAgentVersionId,
                                              List<Long> agentVersionToolIds) {
        return PluginManifestResponse.builder()
                .pluginId(plugin.getId())
                .pluginCode(plugin.getPluginCode())
                .pluginName(plugin.getPluginName())
                .pluginVersion(plugin.getPluginVersion())
                .status(plugin.getStatus())
                .manifestHash(readManifestHash(plugin.getManifestJson()))
                .boundAgentVersionId(boundAgentVersionId)
                .agentVersionToolIds(agentVersionToolIds == null ? List.of() : agentVersionToolIds)
                .tools(tools)
                .build();
    }

    private static String readManifestHash(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) {
            return null;
        }
        String marker = "\"manifestHash\":\"";
        int markerIndex = manifestJson.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int valueStart = markerIndex + marker.length();
        int valueEnd = manifestJson.indexOf('"', valueStart);
        return valueEnd > valueStart ? manifestJson.substring(valueStart, valueEnd) : null;
    }
}
