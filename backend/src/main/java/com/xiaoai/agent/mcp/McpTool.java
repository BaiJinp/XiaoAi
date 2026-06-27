package com.xiaoai.agent.mcp;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具定义
 * Model Context Protocol 工具
 */
@Getter
@Setter
@Builder
public class McpTool {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入参数 schema（JSON Schema）
     */
    private Map<String, Object> inputSchema;

    /**
     * MCP 服务器ID
     */
    private String serverId;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 风险等级
     */
    private String riskLevel;

    /**
     * 转换为平台工具配置
     */
    public com.xiaoai.agent.tool.entity.ToolConfig toToolConfig(Long tenantId) {
        com.xiaoai.agent.tool.entity.ToolConfig config = new com.xiaoai.agent.tool.entity.ToolConfig();
        config.setTenantId(tenantId);
        config.setToolCode("mcp." + serverId + "." + name);
        config.setToolName(name);
        config.setToolType("mcp");
        config.setDescription(description);
        config.setRiskLevel(riskLevel != null ? riskLevel : "medium");
        config.setStatus("active");
        // TODO: 设置 schemaJson
        return config;
    }
}
