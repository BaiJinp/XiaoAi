package com.xiaoai.agent.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 对话会话实体
 */
@Getter
@Setter
@TableName("conversation")
public class Conversation extends TenantEntity {

    /**
     * 对话代码（唯一标识）
     */
    private String conversationCode;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 对话状态（active、archived、deleted）
     */
    private String status;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 对话摘要
     */
    private String summary;

    /**
     * 标签（JSON 数组）
     */
    private String tagsJson;

    /**
     * 父对话ID（用于分支）
     */
    private Long parentConversationId;

    /**
     * 分支点消息ID
     */
    private Long branchPointMessageId;

    /**
     * 最后交互时间
     */
    private java.time.OffsetDateTime lastInteractionAt;

    /**
     * 总 token 使用量
     */
    private Long totalTokens;
}
