package com.xiaoai.agent.tui.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.tui.service.TuiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TUI WebSocket 处理器
 * 提供实时终端交互功能
 */
@Component
public class TuiWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TuiWebSocketHandler.class);

    private final TuiService tuiService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    public TuiWebSocketHandler(TuiService tuiService, ObjectMapper objectMapper) {
        this.tuiService = tuiService;
        this.objectMapper = objectMapper;
    }

    @Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("TUI WebSocket connected: {}", sessionId);

        // 发送欢迎消息
        TuiMessage welcomeMsg = TuiMessage.builder()
                .type("system")
                .content("Welcome to Agent-xiaoAI Terminal Interface")
                .build();
        sendMessage(session, welcomeMsg);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();

        try {
            TuiInput input = objectMapper.readValue(payload, TuiInput.class);

            // 处理输入
            TuiOutput output = tuiService.handleInput(sessionId, input);

            // 发送响应
            TuiMessage responseMsg = TuiMessage.builder()
                    .type("response")
                    .content(output.getContent())
                    .metadata(output.getMetadata())
                    .build();
            sendMessage(session, responseMsg);

        } catch (Exception e) {
            log.error("Error handling TUI message", e);
            TuiMessage errorMsg = TuiMessage.builder()
                    .type("error")
                    .content("Error: " + e.getMessage())
                    .build();
            sendMessage(session, errorMsg);
        }
    }

    @Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("TUI WebSocket disconnected: {} - {}", sessionId, status);

        // 清理会话
        tuiService.cleanupSession(sessionId);
    }

    @Override
public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("TUI WebSocket transport error", exception);
        sessions.remove(session.getId());
    }

    /**
     * 广播消息到所有连接的会话
     */
    public void broadcastMessage(TuiMessage message) {
        for (WebSocketSession session : sessions.values()) {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("Error broadcasting message to session {}", session.getId(), e);
            }
        }
    }

    /**
     * 发送消息到指定会话
     */
    private void sendMessage(WebSocketSession session, TuiMessage message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * 获取当前连接数
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
