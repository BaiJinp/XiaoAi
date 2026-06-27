package com.xiaoai.agent.tui.service;

import com.xiaoai.agent.tui.websocket.TuiInput;
import com.xiaoai.agent.tui.websocket.TuiOutput;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TUI 服务
 * 处理终端界面的输入和输出
 */
@Service
public class TuiService {

    // 会话状态存储
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    /**
     * 处理用户输入
     */
    public TuiOutput handleInput(String sessionId, TuiInput input) {
        SessionState state = sessionStates.computeIfAbsent(sessionId, k -> new SessionState());

        switch (input.getType()) {
            case "command":
                return handleCommand(state, input);
            case "text":
                return handleText(state, input);
            case "shortcut":
                return handleShortcut(state, input);
            default:
                return TuiOutput.builder()
                        .content("Unknown input type: " + input.getType())
                        .completed(true)
                        .build();
        }
    }

    /**
     * 处理命令输入
     */
    private TuiOutput handleCommand(SessionState state, TuiInput input) {
        String command = input.getContent().trim();

        // 处理特殊命令
        if (command.startsWith("/")) {
            return handleSlashCommand(state, command);
        }

        // 普通命令执行
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "command");
        metadata.put("command", command);

        return TuiOutput.builder()
                .content("Executing command: " + command)
                .metadata(metadata)
                .completed(true)
                .build();
    }

    /**
     * 处理斜杠命令
     */
    private TuiOutput handleSlashCommand(SessionState state, String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].substring(1); // 移除 '/'
        String args = parts.length > 1 ? parts[1] : "";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "slash_command");
        metadata.put("command", cmd);
        metadata.put("args", args);

        switch (cmd) {
            case "help":
                return TuiOutput.builder()
                        .content(getHelpText())
                        .metadata(metadata)
                        .completed(true)
                        .build();

            case "clear":
                state.clearHistory();
                return TuiOutput.builder()
                        .content("History cleared")
                        .metadata(metadata)
                        .completed(true)
                        .build();

            case "history":
                return TuiOutput.builder()
                        .content(state.getHistoryText())
                        .metadata(metadata)
                        .completed(true)
                        .build();

            case "status":
                return TuiOutput.builder()
                        .content(state.getStatusText())
                        .metadata(metadata)
                        .completed(true)
                        .build();

            default:
                return TuiOutput.builder()
                        .content("Unknown command: /" + cmd)
                        .metadata(metadata)
                        .completed(true)
                        .build();
        }
    }

    /**
     * 处理文本输入
     */
    private TuiOutput handleText(SessionState state, TuiInput input) {
        String text = input.getContent();
        state.addToHistory("user", text);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "text");
        metadata.put("agentId", input.getAgentId());
        metadata.put("taskId", input.getTaskId());

        // TODO: 集成 Agent 执行逻辑
        String response = "Received: " + text;
        state.addToHistory("assistant", response);

        return TuiOutput.builder()
                .content(response)
                .metadata(metadata)
                .completed(true)
                .build();
    }

    /**
     * 处理快捷键
     */
    private TuiOutput handleShortcut(SessionState state, TuiInput input) {
        String shortcut = input.getShortcut();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "shortcut");
        metadata.put("shortcut", shortcut);

        switch (shortcut) {
            case "ctrl+c":
                return TuiOutput.builder()
                        .content("Interrupt received")
                        .metadata(metadata)
                        .completed(true)
                        .build();

            case "ctrl+l":
                state.clearHistory();
                return TuiOutput.builder()
                        .content("Screen cleared")
                        .metadata(metadata)
                        .completed(true)
                        .build();

            case "ctrl+r":
                return TuiOutput.builder()
                        .content(state.getHistoryText())
                        .metadata(metadata)
                        .completed(true)
                        .build();

            default:
                return TuiOutput.builder()
                        .content("Unknown shortcut: " + shortcut)
                        .metadata(metadata)
                        .completed(true)
                        .build();
        }
    }

    /**
     * 清理会话
     */
    public void cleanupSession(String sessionId) {
        sessionStates.remove(sessionId);
    }

    /**
     * 获取帮助文本
     */
    private String getHelpText() {
        return """
                Available Commands:
                  /help     - Show this help message
                  /clear    - Clear history
                  /history  - Show command history
                  /status   - Show current status

                Shortcuts:
                  Ctrl+C    - Interrupt current operation
                  Ctrl+L    - Clear screen
                  Ctrl+R    - Show history

                Just type your message to chat with the agent.
                """;
    }

    /**
     * 会话状态
     */
    private static class SessionState {
        private final java.util.List<HistoryEntry> history = new java.util.ArrayList<>();
public void addToHistory(String role, String content) {
            history.add(new HistoryEntry(role, content, System.currentTimeMillis()));
        }
public void clearHistory() {
            history.clear();
        }
public String getHistoryText() {
            if (history.isEmpty()) {
                return "No history";
            }

            StringBuilder sb = new StringBuilder();
            for (HistoryEntry entry : history) {
                sb.append("[").append(entry.role).append("] ")
                  .append(entry.content).append("\n");
            }
            return sb.toString();
        }
public String getStatusText() {
            return String.format("Session active, %d messages in history", history.size());
        }

        private static class HistoryEntry {
            final String role;
            final String content;
            final long timestamp;

            HistoryEntry(String role, String content, long timestamp) {
                this.role = role;
                this.content = content;
                this.timestamp = timestamp;
            }
        }
    }
}
