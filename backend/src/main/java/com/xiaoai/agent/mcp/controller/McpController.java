package com.xiaoai.agent.mcp.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.mcp.McpServer;
import com.xiaoai.agent.mcp.service.McpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器管理控制器
 */
@RestController
@RequestMapping("/api/v1/mcp")
@CrossOrigin(origins = "*")
public class McpController {

    private final McpService mcpService;

    @Autowired
    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    /**
     * 获取所有 MCP 服务器
     */
    @GetMapping("/servers")
    public ApiResponse<List<McpServer>> listServers(@RequestParam Long tenantId) {
        return ApiResponse.success(mcpService.listServers(tenantId));
    }

    /**
     * 注册 MCP 服务器
     */
    @PostMapping("/servers")
    public ApiResponse<McpServer> registerServer(
            @RequestParam Long tenantId,
            @RequestBody McpServer server) {
        try {
            McpServer registered = mcpService.registerServer(tenantId, server);
            return ApiResponse.success(registered);
        } catch (Exception e) {
            return ApiResponse.error("Failed to register server: " + e.getMessage());
        }
    }

    /**
     * 连接 MCP 服务器
     */
    @PostMapping("/servers/{serverCode}/connect")
    public ApiResponse<String> connectServer(@PathVariable String serverCode) {
        try {
            mcpService.connectServer(serverCode);
            return ApiResponse.success("Connected to MCP server: " + serverCode);
        } catch (Exception e) {
            return ApiResponse.error("Failed to connect: " + e.getMessage());
        }
    }

    /**
     * 断开 MCP 服务器
     */
    @PostMapping("/servers/{serverCode}/disconnect")
    public ApiResponse<String> disconnectServer(@PathVariable String serverCode) {
        try {
            mcpService.disconnectServer(serverCode);
            return ApiResponse.success("Disconnected from MCP server: " + serverCode);
        } catch (Exception e) {
            return ApiResponse.error("Failed to disconnect: " + e.getMessage());
        }
    }

    /**
     * 删除 MCP 服务器
     */
    @DeleteMapping("/servers/{serverCode}")
    public ApiResponse<String> deleteServer(@PathVariable String serverCode) {
        try {
            mcpService.deleteServer(serverCode);
            return ApiResponse.success("Deleted MCP server: " + serverCode);
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete: " + e.getMessage());
        }
    }

    /**
     * 获取已连接的服务器
     */
    @GetMapping("/servers/connected")
    public ApiResponse<List<String>> getConnectedServers() {
        return ApiResponse.success(mcpService.getConnectedServers());
    }

    /**
     * 获取服务器状态
     */
    @GetMapping("/servers/status")
    public ApiResponse<Map<String, Boolean>> getServerStatus() {
        return ApiResponse.success(mcpService.getServerStatus());
    }

    /**
     * 调用 MCP 工具
     */
    @PostMapping("/tools/call")
    public ApiResponse<Map<String, Object>> callTool(
            @RequestParam String serverCode,
            @RequestParam String toolName,
            @RequestBody Map<String, Object> arguments) {
        try {
            Map<String, Object> result = mcpService.callMcpTool(serverCode, toolName, arguments);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error("Failed to call tool: " + e.getMessage());
        }
    }
}
