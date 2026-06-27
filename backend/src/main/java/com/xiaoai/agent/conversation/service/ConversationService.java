package com.xiaoai.agent.conversation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.conversation.entity.Conversation;
import com.xiaoai.agent.conversation.entity.ConversationMessage;

import java.util.List;

/**
 * 对话服务接口
 * <p>
 * 管理用户与Agent之间的对话历史，提供对话创建、消息追加、压缩、搜索、分支、回退、导出和归档等功能。
 * 对话是Agent多轮交互的核心载体，每条消息记录role（user/assistant/system）和content。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 创建新对话
     * <p>
     * 初始化一个空的对话会话，关联指定的租户、用户和Agent。
     * 对话初始状态为active，可通过title设置对话主题。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param agentId  Agent ID
     * @param title    对话标题（可选）
     * @return 创建后的Conversation实体
     */
    Conversation createConversation(Long tenantId, Long userId, Long agentId, String title);

    /**
     * 向对话追加一条消息
     * <p>
     * 在对话末尾追加一条指定role的消息，role通常为"user"、"assistant"或"system"。
     * 可选关联taskId，用于追踪由哪个Task产生的消息。
     * </p>
     *
     * @param conversationId 对话ID
     * @param role           消息角色（user/assistant/system）
     * @param content        消息内容
     * @param taskId         关联的任务ID（可选，可为null）
     * @return 创建后的ConversationMessage实体
     */
    ConversationMessage addMessage(Long conversationId, String role, String content, Long taskId);

    /**
     * 获取对话的所有消息列表，按发送时间顺序排列。
     *
     * @param conversationId 对话ID
     * @return 消息列表
     */
    List<ConversationMessage> getMessages(Long conversationId);

    /**
     * 压缩对话历史
     * <p>
     * 将对话的完整消息历史压缩为一段摘要文本，用于减少LLM上下文长度。
     * 压缩后原始消息保留，摘要存储在Conversation的summaryText字段中。
     * </p>
     *
     * @param conversationId 对话ID
     * @return 压缩后的摘要文本
     */
    String compressConversation(Long conversationId);

    /**
     * 搜索对话
     * <p>
     * 在租户的对话标题中进行关键词模糊搜索，按最近活跃时间倒序排列。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param keyword  搜索关键词（在标题中匹配）
     * @param limit    最大返回数量
     * @return 匹配的对话列表
     */
    List<Conversation> searchConversations(Long tenantId, Long userId, String keyword, int limit);

    /**
     * 从对话的某个消息节点创建分支
     * <p>
     * 复制对话中branchPointMessageId及之前的所有消息到新对话，
     * 用于在对话历史中"分叉"探索不同的对话路径。
     * </p>
     *
     * @param conversationId       原对话ID
     * @param branchPointMessageId 分支起点消息ID（包含该消息）
     * @param newTitle             新对话标题
     * @return 新创建的分支Conversation实体
     */
    Conversation branchConversation(Long conversationId, Long branchPointMessageId, String newTitle);

    /**
     * 回退对话到指定消息
     * <p>
     * 删除messageId之后的所有消息，用于撤销错误的对话步骤或重新探索。
     * messageId本身保留，该消息之后追加的所有消息将被删除。
     * </p>
     *
     * @param conversationId 对话ID
     * @param messageId      回退目标消息ID（保留该消息）
     */
    void rollbackToMessage(Long conversationId, Long messageId);

    /**
     * 导出对话为指定格式的字符串
     * <p>
     * 支持格式：
     * <ul>
     *   <li>"json" - JSON格式，含对话元数据和所有消息</li>
     *   <li>"markdown" - Markdown格式，便于阅读</li>
     *   <li>其他 - 纯文本格式</li>
     * </ul>
     * </p>
     *
     * @param conversationId 对话ID
     * @param format         导出格式："json"、"markdown"或其他
     * @return 格式化后的对话字符串
     */
    String exportConversation(Long conversationId, String format);

    /**
     * 归档对话，将对话状态从active改为archived。
     * 归档后对话不再出现在活跃列表中，但历史消息保留可查。
     *
     * @param conversationId 对话ID
     */
    void archiveConversation(Long conversationId);

    /**
     * 获取对话统计信息，包括消息总数、总token数、用户消息数和助手消息数。
     *
     * @param conversationId 对话ID
     * @return ConversationStats统计对象
     */
    ConversationStats getStats(Long conversationId);

    /**
     * 对话统计数据对象
     * <p>
     * 记录对话的量化指标，用于展示对话规模和消耗情况。
     * </p>
     */
    class ConversationStats {
        /** 消息总数（包含user、assistant、system所有角色） */
        public int messageCount;
        /** 估算的总token数（用于成本核算） */
        public long totalTokens;
        /** 用户发送的消息数 */
        public int userMessageCount;
        /** Agent回复的消息数 */
        public int assistantMessageCount;
    }
}
