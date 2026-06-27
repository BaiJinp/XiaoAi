package com.xiaoai.agent.tui.config;

import com.xiaoai.agent.tui.websocket.TuiWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * TUI WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class TuiWebSocketConfig implements WebSocketConfigurer {

    private final TuiWebSocketHandler tuiWebSocketHandler;

    @Autowired
    public TuiWebSocketConfig(TuiWebSocketHandler tuiWebSocketHandler) {
        this.tuiWebSocketHandler = tuiWebSocketHandler;
    }

    @Override
public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tuiWebSocketHandler, "/ws/tui")
                .setAllowedOrigins("*");
    }
}
