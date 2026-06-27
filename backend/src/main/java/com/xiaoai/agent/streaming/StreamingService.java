package com.xiaoai.agent.streaming;

import com.xiaoai.agent.runtime.model.RuntimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式服务
 * 管理 SSE 连接和事件发布
 */
@Service
public class StreamingService {

    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);

    /**
     * 会话流映射：sessionId -> Sink
     */
    private final Map<String, Sinks.Many<StreamingEvent>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * 会话运行时事件映射：sessionId -> 事件列表
     */
    private final Map<String, java.util.List<RuntimeEvent>> sessionEvents = new ConcurrentHashMap<>();

    /**
     * 创建新的流式会话
     */
    public String createSession() {
        String sessionId = java.util.UUID.randomUUID().toString();
        Sinks.Many<StreamingEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);
        sessionEvents.put(sessionId, new java.util.ArrayList<>());

        log.info("Created streaming session: {}", sessionId);
        return sessionId;
    }

    /**
     * 获取会话的流式数据
     */
    public Flux<StreamingEvent> getStream(String sessionId) {
        Sinks.Many<StreamingEvent> sink = sessionSinks.get(sessionId);
        if (sink == null) {
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }

        // 添加心跳保持连接
        return sink.asFlux()
                .mergeWith(Flux.interval(Duration.ofSeconds(30))
                        .map(i -> StreamingEvent.heartbeat()));
    }

    /**
     * 发布事件到会话
     */
    public void publish(String sessionId, StreamingEvent event) {
        Sinks.Many<StreamingEvent> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(event);
            log.debug("Published event to session {}: {}", sessionId, event.getEvent());
        }
    }

    /**
     * 发布文本事件
     */
    public void publishText(String sessionId, String text) {
        publish(sessionId, StreamingEvent.text(text));
    }

    /**
     * 发布进度事件
     */
    public void publishProgress(String sessionId, String phase, String message, Integer percent) {
        publish(sessionId, StreamingEvent.progress(phase, message, percent));
    }

    /**
     * 发布工具调用事件
     */
    public void publishToolCall(String sessionId, String toolName, String status, String result) {
        publish(sessionId, StreamingEvent.toolCall(toolName, status, result));
    }

    /**
     * 发布完成事件
     */
    public void publishComplete(String sessionId, String summary) {
        publish(sessionId, StreamingEvent.complete(summary));
        closeSession(sessionId);
    }

    /**
     * 发布错误事件
     */
    public void publishError(String sessionId, String message) {
        publish(sessionId, StreamingEvent.error(message));
    }

    /**
     * 记录运行时事件
     */
    public void recordRuntimeEvent(String sessionId, RuntimeEvent event) {
        java.util.List<RuntimeEvent> events = sessionEvents.get(sessionId);
        if (events != null) {
            events.add(event);

            // 根据事件类型发布相应的流式事件
            switch (event.getEventType()) {
                case "MODEL_CALL":
                    publishProgress(sessionId, "model", "调用模型中...", null);
                    break;
                case "TOOL_CALL":
                    publishToolCall(sessionId, "tool", "calling", null);
                    break;
                case "TOOL_RESULT":
                    publishToolCall(sessionId, "tool", "completed", event.getPayloadJson());
                    break;
                case "KNOWLEDGE_RETRIEVE":
                    publishProgress(sessionId, "knowledge", "检索知识中...", null);
                    break;
                default:
                    // 其他事件类型
                    break;
            }
        }
    }

    /**
     * 获取会话的所有运行时事件
     */
    public java.util.List<RuntimeEvent> getRuntimeEvents(String sessionId) {
        return sessionEvents.getOrDefault(sessionId, java.util.List.of());
    }

    /**
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        Sinks.Many<StreamingEvent> sink = sessionSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Closed streaming session: {}", sessionId);
        }
        sessionEvents.remove(sessionId);
    }

    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(String sessionId) {
        return sessionSinks.containsKey(sessionId);
    }

    /**
     * 获取活跃会话数
     */
    public int getActiveSessionCount() {
        return sessionSinks.size();
    }
}
