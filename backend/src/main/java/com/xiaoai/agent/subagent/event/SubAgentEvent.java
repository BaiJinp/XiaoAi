package com.xiaoai.agent.subagent.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 子代理事件基类
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentEvent {

    /**
     * 事件类型（PROGRESS/RESULT/ERROR/HEARTBEAT）
     */
    private EventType eventType;

    /**
     * 子代理代码
     */
    private String subAgentCode;

    /**
     * 父运行ID
     */
    private Long parentRunId;

    /**
     * 时间戳
     */
    private OffsetDateTime timestamp;

    /**
     * 载荷（JSON字符串）
     */
    private String payload;

    public enum EventType {
        PROGRESS,
        RESULT,
        ERROR,
        HEARTBEAT
    }
}
