package com.xiaoai.agent.plugin.model;

import com.xiaoai.agent.tool.entity.ToolConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PluginToolResponse {

    private final Long toolId;

    private final String toolCode;

    private final String toolName;

    private final String toolType;

    private final String riskLevel;

    private final String status;

    private final String schemaJson;

    private final Boolean bound;

    private final Long boundAgentId;

    private final Long bindingId;

    private final String pluginCode;

    private final String pluginVersion;

    private final String manifestHash;

    public static PluginToolResponse from(ToolConfig tool) {
        return from(tool, null, null);
    }

    public static PluginToolResponse from(ToolConfig tool, Long boundAgentId, Long bindingId) {
        return PluginToolResponse.builder()
                .toolId(tool.getId())
                .toolCode(tool.getToolCode())
                .toolName(tool.getToolName())
                .toolType(tool.getToolType())
                .riskLevel(tool.getRiskLevel())
                .status(tool.getStatus())
                .schemaJson(tool.getSchemaJson())
                .bound(boundAgentId != null)
                .boundAgentId(boundAgentId)
                .bindingId(bindingId)
                .pluginCode(readPluginAudit(tool.getAuthConfigJson(), "pluginCode"))
                .pluginVersion(readPluginAudit(tool.getAuthConfigJson(), "pluginVersion"))
                .manifestHash(readPluginAudit(tool.getAuthConfigJson(), "manifestHash"))
                .build();
    }

    private static String readPluginAudit(String authConfigJson, String fieldName) {
        if (authConfigJson == null || authConfigJson.isBlank()) {
            return null;
        }
        String pluginMarker = "\"_plugin\"";
        int pluginIndex = authConfigJson.indexOf(pluginMarker);
        if (pluginIndex < 0) {
            return null;
        }
        String marker = "\"" + fieldName + "\":\"";
        int markerIndex = authConfigJson.indexOf(marker, pluginIndex);
        if (markerIndex < 0) {
            return null;
        }
        int valueStart = markerIndex + marker.length();
        int valueEnd = authConfigJson.indexOf('"', valueStart);
        return valueEnd > valueStart ? authConfigJson.substring(valueStart, valueEnd) : null;
    }
}
