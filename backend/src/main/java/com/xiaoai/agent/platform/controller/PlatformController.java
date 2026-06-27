package com.xiaoai.agent.platform.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.platform.PlatformGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台集成控制器
 * 提供平台适配器的管理 API
 */
@RestController
@RequestMapping("/api/v1/platforms")
@CrossOrigin(origins = "*")
public class PlatformController {

    private final PlatformGateway platformGateway;

    @Autowired
    public PlatformController(PlatformGateway platformGateway) {
        this.platformGateway = platformGateway;
    }

    /**
     * 获取所有已注册的平台
     */
    @GetMapping
    public ApiResponse<List<String>> getRegisteredPlatforms() {
        return ApiResponse.success(platformGateway.getRegisteredPlatforms());
    }

    /**
     * 获取所有平台适配器的状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> getAdapterStatus() {
        return ApiResponse.success(platformGateway.getAdapterStatus());
    }

    /**
     * 启动指定平台的适配器
     */
    @PostMapping("/{platformType}/start")
    public ApiResponse<String> startAdapter(@PathVariable String platformType) {
        try {
            platformGateway.startAdapter(platformType);
            return ApiResponse.success("Platform adapter started: " + platformType);
        } catch (Exception e) {
            return ApiResponse.error("Failed to start adapter: " + e.getMessage());
        }
    }

    /**
     * 停止指定平台的适配器
     */
    @PostMapping("/{platformType}/stop")
    public ApiResponse<String> stopAdapter(@PathVariable String platformType) {
        try {
            platformGateway.stopAdapter(platformType);
            return ApiResponse.success("Platform adapter stopped: " + platformType);
        } catch (Exception e) {
            return ApiResponse.error("Failed to stop adapter: " + e.getMessage());
        }
    }

    /**
     * 启动所有平台适配器
     */
    @PostMapping("/start-all")
    public ApiResponse<String> startAllAdapters() {
        platformGateway.startAll();
        return ApiResponse.success("All platform adapters started");
    }

    /**
     * 停止所有平台适配器
     */
    @PostMapping("/stop-all")
    public ApiResponse<String> stopAllAdapters() {
        platformGateway.stopAll();
        return ApiResponse.success("All platform adapters stopped");
    }

    /**
     * 发送测试消息到指定平台
     */
    @PostMapping("/{platformType}/test")
    public ApiResponse<Map<String, Object>> sendTestMessage(
            @PathVariable String platformType,
            @RequestParam String chatId,
            @RequestParam String message) {
        boolean success = platformGateway.sendMessage(platformType, chatId, message);

        Map<String, Object> result = new HashMap<>();
        result.put("platform", platformType);
        result.put("chatId", chatId);
        result.put("success", success);

        if (success) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error("Failed to send message to " + platformType);
        }
    }

    /**
     * 获取平台用户信息
     */
    @GetMapping("/{platformType}/users/{userId}")
    public ApiResponse<String> getUserInfo(
            @PathVariable String platformType,
            @PathVariable String userId) {
        // TODO: 从 PlatformGateway 获取适配器并调用 getUserInfo
        return ApiResponse.success("{\"userId\":\"" + userId + "\",\"platform\":\"" + platformType + "\"}");
    }

    /**
     * 获取平台群组信息
     */
    @GetMapping("/{platformType}/chats/{chatId}")
    public ApiResponse<String> getChatInfo(
            @PathVariable String platformType,
            @PathVariable String chatId) {
        // TODO: 从 PlatformGateway 获取适配器并调用 getChatInfo
        return ApiResponse.success("{\"chatId\":\"" + chatId + "\",\"platform\":\"" + platformType + "\"}");
    }
}
