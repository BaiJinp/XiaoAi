package com.xiaoai.agent.platform;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 平台消息
 * 统一不同平台的消息格式
 */
@Getter
@Setter
@Builder
public class PlatformMessage {

    /**
     * 消息ID（平台内唯一）
     */
    private String messageId;

    /**
     * 平台类型（telegram、discord、slack、wechat、dingtalk、web）
     */
    private String platformType;

    /**
     * 平台用户ID
     */
    private String platformUserId;

    /**
     * 平台用户名
     */
    private String platformUserName;

    /**
     * 平台会话ID（群组/频道/私聊）
     */
    private String platformChatId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型（text、image、voice、file）
     */
    private String messageType;

    /**
     * 回复的消息ID
     */
    private String replyToMessageId;

    /**
     * 是否来自群组
     */
    private Boolean isGroup;

    /**
     * 时间戳
     */
    private OffsetDateTime timestamp;

    /**
     * 原始消息数据（平台特定）
     */
    private Map<String, Object> rawData;

    /**
     * 创建文本消息
     */
    public static PlatformMessage text(String platformType, String userId, String chatId, String content) {
        return PlatformMessage.builder()
                .platformType(platformType)
                .platformUserId(userId)
                .platformChatId(chatId)
                .content(content)
                .messageType("text")
                .isGroup(false)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    /**
     * 创建群组消息
     */
    public static PlatformMessage groupText(String platformType, String userId, String chatId, String content) {
        return PlatformMessage.builder()
                .platformType(platformType)
                .platformUserId(userId)
                .platformChatId(chatId)
                .content(content)
                .messageType("text")
                .isGroup(true)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
