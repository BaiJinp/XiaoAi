package com.xiaoai.agent.terminal.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.terminal.TerminalBackend;
import com.xiaoai.agent.terminal.TerminalBackendRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 终端后端控制器
 */
@RestController
@RequestMapping("/api/v1/terminals")
@CrossOrigin(origins = "*")
public class TerminalController {

    private final TerminalBackendRegistry backendRegistry;

    @Autowired
    public TerminalController(TerminalBackendRegistry backendRegistry) {
        this.backendRegistry = backendRegistry;
    }

    /**
     * 获取所有后端类型
     */
    @GetMapping("/types")
    public ApiResponse<List<String>> getBackendTypes() {
        return ApiResponse.success(backendRegistry.getBackendTypes());
    }

    /**
     * 获取所有可用后端
     */
    @GetMapping("/available")
    public ApiResponse<List<Map<String, Object>>> getAvailableBackends() {
        List<Map<String, Object>> backends = backendRegistry.getAvailableBackends().stream()
                .map(b -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("type", b.getBackendType());
                    info.put("status", b.getStatus().name());
                    return info;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(backends);
    }

    /**
     * 检查后端是否可用
     */
    @GetMapping("/{backendType}/status")
    public ApiResponse<Map<String, Object>> getBackendStatus(@PathVariable String backendType) {
        Map<String, Object> status = new HashMap<>();
        status.put("type", backendType);
        status.put("available", backendRegistry.isBackendAvailable(backendType));
        status.put("status", backendRegistry.getBackendStatus(backendType).name());

        return ApiResponse.success(status);
    }

    /**
     * 执行命令
     */
    @PostMapping("/execute")
    public ApiResponse<TerminalBackend.TerminalResult> executeCommand(@RequestBody Map<String, Object> request) {
        String backendType = (String) request.getOrDefault("backendType", "local");
        String command = (String) request.get("command");

        if (command == null || command.trim().isEmpty()) {
            return ApiResponse.error("Command is required");
        }

        TerminalBackend backend = backendRegistry.getBackend(backendType);

        if (!backend.isAvailable()) {
            return ApiResponse.error("Backend " + backendType + " is not available");
        }

        // 构建配置
        TerminalBackend.TerminalConfig config = new TerminalBackend.TerminalConfig();

        @SuppressWarnings("unchecked")
        Map<String, String> environment = (Map<String, String>) request.get("environment");
        if (environment != null) {
            config.setEnvironment(environment);
        }

        String workingDirectory = (String) request.get("workingDirectory");
        if (workingDirectory != null) {
            config.setWorkingDirectory(workingDirectory);
        }

        Object timeoutObj = request.get("timeoutMs");
        if (timeoutObj != null) {
            config.setTimeoutMs(Long.parseLong(timeoutObj.toString()));
        }

        // 执行命令
        TerminalBackend.TerminalResult result = backend.execute(command, config);

        return ApiResponse.success(result);
    }
}
