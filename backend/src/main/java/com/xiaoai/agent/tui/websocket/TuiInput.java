package com.xiaoai.agent.tui.websocket;

import lombok.Data;

/**
 * TUI 输入模型
 */
@Data
public class TuiInput {
    /**
     * 输入类型：command, text, shortcut
     */
    private String type;

    /**
     * 输入内容
     */
    private String content;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 代理ID
     */
    private Long agentId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 快捷键
     */
    private String shortcut;
}
