package com.xiaoai.agent.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 对话消息实体
 */
@Getter
@Setter
@TableName("conversation_message")
public class ConversationMessage extends TenantEntity {

    /**
     * 消息代码（唯一标识）
     */
    private String messageCode;

    /**
     * 对话ID
     */
    private Long conversationId;

    /**
     * 消息角色（user、assistant、system）
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息序号（在对话中的顺序）
     */
    private Integer sequenceNumber;

    /**
     * 父消息ID（用于分支）
     */
    private Long parentMessageId;

    /**
     * 关联的任务ID
     */
    private Long taskId;

    /**
     * 关联的运行ID
     */
    private Long runId;

    /**
     * Token 使用量
     */
    private Integer tokenCount;

    /**
     * 元数据（JSON）
     */
    private String metadataJson;

    /**
     * 是否已压缩
     */
    private Boolean compressed;

    /**
     * 压缩后的内容
     */
    private String compressedContent;
}
