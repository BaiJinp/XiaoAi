package com.xiaoai.agent.tui.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * TUI 输出模型
 */
@Data
@Builder
public class TuiOutput {
    /**
     * 输出内容
     */
    private String content;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 是否完成
     */
    private boolean completed;

    /**
     * Token 使用量
     */
    private Integer tokenUsage;
}
