package com.xiaoai.agent.subagent.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 子代理消息（POJO，非数据库实体）
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentMessage {

    /**
     * 消息代码
     */
    private String messageCode;

    /**
     * 发送方子代理代码
     */
    private String senderSubAgentCode;

    /**
     * 目标子代理代码（null表示广播）
     */
    private String targetSubAgentCode;

    /**
     * 方向（PARENT_TO_CHILD / CHILD_TO_PARENT / SIBLING）
     */
    private Direction direction;

    /**
     * 消息类型（TEXT / PROGRESS / RESULT / CORRECTION / CANCEL）
     */
    private MessageType messageType;

    /**
     * 内容
     */
    private String content;

    /**
     * 状态（PENDING / DELIVERED / READ）
     */
    private Status status;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    public enum Direction {
        PARENT_TO_CHILD,
        CHILD_TO_PARENT,
        SIBLING
    }

    public enum MessageType {
        TEXT,
        PROGRESS,
        RESULT,
        CORRECTION,
        CANCEL
    }

    public enum Status {
        PENDING,
        DELIVERED,
        READ
    }
}
