package com.xiaoai.agent.mcp.service;

import com.xiaoai.agent.mcp.McpServer;
import com.xiaoai.agent.mcp.McpTool;
import com.xiaoai.agent.mcp.client.McpClient;
import com.xiaoai.agent.mcp.mapper.McpServerMapper;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.service.ToolConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 服务
 * 管理 MCP 服务器和工具
 */
@Service
public class McpService {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    private final McpServerMapper mcpServerMapper;
    private final ToolConfigService toolConfigService;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    @Autowired
    public McpService(McpServerMapper mcpServerMapper, ToolConfigService toolConfigService) {
        this.mcpServerMapper = mcpServerMapper;
        this.toolConfigService = toolConfigService;
    }

    /**
     * 注册 MCP 服务器
     */
    public McpServer registerServer(Long tenantId, McpServer server) {
        server.setTenantId(tenantId);
        server.setStatus("active");
        mcpServerMapper.insert(server);
        log.info("Registered MCP server: code={}, name={}", server.getServerCode(), server.getServerName());
        return server;
    }

    /**
     * 连接 MCP 服务器
     */
    public void connectServer(String serverCode) throws Exception {
        McpServer server = getServerByCode(serverCode);
        if (server == null) {
            throw new IllegalArgumentException("MCP server not found: " + serverCode);
        }

        McpClient client = new McpClient(serverCode, server.getServerType(), server.getServerUrl());
        client.connect();
        clients.put(serverCode, client);

        // 获取工具列表并同步到工具配置
        syncToolsFromServer(server);

        log.info("Connected to MCP server: {}", serverCode);
    }

    /**
     * 断开 MCP 服务器
     */
    public void disconnectServer(String serverCode) {
        McpClient client = clients.remove(serverCode);
        if (client != null) {
            client.disconnect();
            log.info("Disconnected from MCP server: {}", serverCode);
        }
    }

    /**
     * 同步服务器工具到平台
     */
    private void syncToolsFromServer(McpServer server) {
        McpClient client = clients.get(server.getServerCode());
        if (client == null || !client.isConnected()) {
            log.warn("MCP client not connected: {}", server.getServerCode());
            return;
        }

        List<McpTool> tools = client.listTools();
        log.info("Syncing {} tools from MCP server: {}", tools.size(), server.getServerCode());

        for (McpTool mcpTool : tools) {
            // 转换为平台工具配置
            ToolConfig toolConfig = mcpTool.toToolConfig(server.getTenantId());

            // 检查是否已存在
            ToolConfig existing = toolConfigService.getToolConfigByCode(
                    server.getTenantId(), toolConfig.getToolCode());

            if (existing == null) {
                // 创建新工具
                toolConfigService.createToolConfig(toolConfig);
                log.info("Created tool from MCP: {}", toolConfig.getToolCode());
            } else {
                // 更新现有工具
                toolConfig.setToolName(mcpTool.getName());
                toolConfig.setDescription(mcpTool.getDescription());
                toolConfigService.updateToolConfig(existing.getId(), toolConfig);
                log.info("Updated tool from MCP: {}", toolConfig.getToolCode());
            }
        }

        // 更新服务器工具数量
        server.setToolCount(tools.size());
        mcpServerMapper.updateById(server);
    }

    /**
     * 调用 MCP 工具
     */
    public Map<String, Object> callMcpTool(String serverCode, String toolName, Map<String, Object> arguments) {
        McpClient client = clients.get(serverCode);
        if (client == null || !client.isConnected()) {
            log.warn("MCP client not connected: {}", serverCode);
            return Map.of("error", "MCP server not connected");
        }

        return client.callTool(toolName, arguments);
    }

    /**
     * 获取所有 MCP 服务器
     */
    public List<McpServer> listServers(Long tenantId) {
        return mcpServerMapper.selectList(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getTenantId, tenantId));
    }

    /**
     * 根据代码获取服务器
     */
    public McpServer getServerByCode(String serverCode) {
        return mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getServerCode, serverCode));
    }

    /**
     * 获取已连接的服务器
     */
    public List<String> getConnectedServers() {
        return clients.entrySet().stream()
                .filter(e -> e.getValue().isConnected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取服务器状态
     */
    public Map<String, Boolean> getServerStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
            status.put(entry.getKey(), entry.getValue().isConnected());
        }
        return status;
    }

    /**
     * 删除 MCP 服务器
     */
    public void deleteServer(String serverCode) {
        // 先断开连接
        disconnectServer(serverCode);

        // 删除服务器配置
        mcpServerMapper.delete(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getServerCode, serverCode));

        log.info("Deleted MCP server: {}", serverCode);
    }
}
