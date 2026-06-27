package com.xiaoai.agent.streaming.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.streaming.StreamingEvent;
import com.xiaoai.agent.streaming.StreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 流式输出控制器
 * 提供 SSE (Server-Sent Events) 端点
 */
@RestController
@RequestMapping("/api/v1/streaming")
@CrossOrigin(origins = "*")
public class StreamingController {

    private final StreamingService streamingService;

    @Autowired
    public StreamingController(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * 创建流式会话
     */
    @PostMapping("/sessions")
    public ApiResponse<Map<String, String>> createSession() {
        String sessionId = streamingService.createSession();
        Map<String, String> result = new HashMap<>();
        result.put("sessionId", sessionId);
        return ApiResponse.success(result);
    }

    /**
     * SSE 流式端点
     * 客户端通过此端点接收实时事件
     */
    @GetMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamingEvent> streamEvents(@PathVariable String sessionId) {
        if (!streamingService.sessionExists(sessionId)) {
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }

        return streamingService.getStream(sessionId);
    }

    /**
     * 发布测试事件（用于调试）
     */
    @PostMapping("/sessions/{sessionId}/test")
    public ApiResponse<String> publishTestEvent(
            @PathVariable String sessionId,
            @RequestParam String type,
            @RequestParam(required = false) String data) {

        if (!streamingService.sessionExists(sessionId)) {
            return ApiResponse.error("Session not found");
        }

        switch (type) {
            case "text":
                streamingService.publishText(sessionId, data != null ? data : "Test message");
                break;
            case "progress":
                streamingService.publishProgress(sessionId, "test", "Testing...", 50);
                break;
            case "complete":
                streamingService.publishComplete(sessionId, data != null ? data : "Test completed");
                break;
            case "error":
                streamingService.publishError(sessionId, data != null ? data : "Test error");
                break;
            default:
                return ApiResponse.error("Unknown event type: " + type);
        }

        return ApiResponse.success("Event published");
    }

    /**
     * 关闭会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<String> closeSession(@PathVariable String sessionId) {
        streamingService.closeSession(sessionId);
        return ApiResponse.success("Session closed");
    }

    /**
     * 获取活跃会话数
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", streamingService.getActiveSessionCount());
        return ApiResponse.success(stats);
    }
}
