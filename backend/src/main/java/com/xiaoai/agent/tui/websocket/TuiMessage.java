package com.xiaoai.agent.tui.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * TUI 消息模型
 */
@Data
@Builder
public class TuiMessage {
    /**
     * 消息类型：system, response, error, stream
     */
    private String type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;
}
