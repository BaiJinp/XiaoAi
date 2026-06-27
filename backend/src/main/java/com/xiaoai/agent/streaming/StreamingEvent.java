package com.xiaoai.agent.streaming;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 流式事件
 * 用于 Server-Sent Events (SSE) 输出
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamingEvent {

    /**
     * 事件类型
     */
    private String event;

    /**
     * 事件数据
     */
    private String data;

    /**
     * 事件ID
     */
    private String id;

    /**
     * 重试时间（毫秒）
     */
    private Integer retry;

    /**
     * 创建文本事件
     */
    public static StreamingEvent text(String text) {
        return StreamingEvent.builder()
                .event("text")
                .data(text)
                .build();
    }

    /**
     * 创建进度事件
     */
    public static StreamingEvent progress(String phase, String message, Integer percent) {
        return StreamingEvent.builder()
                .event("progress")
                .data(String.format("{\"phase\":\"%s\",\"message\":\"%s\",\"percent\":%d}",
                        phase, message, percent))
                .build();
    }

    /**
     * 创建工具调用事件
     */
    public static StreamingEvent toolCall(String toolName, String status, String result) {
        return StreamingEvent.builder()
                .event("tool")
                .data(String.format("{\"tool\":\"%s\",\"status\":\"%s\",\"result\":\"%s\"}",
                        toolName, status, result != null ? result : ""))
                .build();
    }

    /**
     * 创建完成事件
     */
    public static StreamingEvent complete(String summary) {
        return StreamingEvent.builder()
                .event("complete")
                .data(String.format("{\"summary\":\"%s\"}", summary))
                .build();
    }

    /**
     * 创建错误事件
     */
    public static StreamingEvent error(String message) {
        return StreamingEvent.builder()
                .event("error")
                .data(String.format("{\"message\":\"%s\"}", message))
                .build();
    }

    /**
     * 创建心跳事件（保持连接）
     */
    public static StreamingEvent heartbeat() {
        return StreamingEvent.builder()
                .event("heartbeat")
                .data("")
                .build();
    }

    /**
     * 格式化为 SSE 格式
     */
    public String toSSEFormat() {
        StringBuilder sb = new StringBuilder();

        if (event != null) {
            sb.append("event: ").append(event).append("\n");
        }

        if (data != null) {
            sb.append("data: ").append(data).append("\n");
        }

        if (id != null) {
            sb.append("id: ").append(id).append("\n");
        }

        if (retry != null) {
            sb.append("retry: ").append(retry).append("\n");
        }

        sb.append("\n");
        return sb.toString();
    }
}
