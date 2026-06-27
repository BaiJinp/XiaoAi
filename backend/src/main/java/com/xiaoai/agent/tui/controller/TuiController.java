package com.xiaoai.agent.tui.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.tui.service.TuiService;
import com.xiaoai.agent.tui.websocket.TuiWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * TUI 控制器
 * 提供 HTTP 接口用于查询 TUI 状态
 */
@RestController
@RequestMapping("/api/v1/tui")
@CrossOrigin(origins = "*")
public class TuiController {

    private final TuiWebSocketHandler webSocketHandler;
    private final TuiService tuiService;

    @Autowired
    public TuiController(TuiWebSocketHandler webSocketHandler, TuiService tuiService) {
        this.webSocketHandler = webSocketHandler;
        this.tuiService = tuiService;
    }

    /**
     * 获取 TUI 状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeSessions", webSocketHandler.getActiveSessionCount());
        status.put("webSocketEndpoint", "/ws/tui");
        status.put("status", "running");

        return ApiResponse.success(status);
    }

    /**
     * 获取帮助信息
     */
    @GetMapping("/help")
    public ApiResponse<Map<String, Object>> getHelp() {
        Map<String, Object> help = new HashMap<>();
        help.put("commands", new String[]{
                "/help - Show help message",
                "/clear - Clear history",
                "/history - Show command history",
                "/status - Show current status"
        });
        help.put("shortcuts", new String[]{
                "Ctrl+C - Interrupt current operation",
                "Ctrl+L - Clear screen",
                "Ctrl+R - Show history"
        });
        help.put("description", "Terminal Interface (TUI) for Agent-xiaoAI");

        return ApiResponse.success(help);
    }
}
