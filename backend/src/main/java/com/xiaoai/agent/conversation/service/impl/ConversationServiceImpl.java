package com.xiaoai.agent.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.conversation.entity.Conversation;
import com.xiaoai.agent.conversation.entity.ConversationMessage;
import com.xiaoai.agent.conversation.mapper.ConversationMapper;
import com.xiaoai.agent.conversation.mapper.ConversationMessageMapper;
import com.xiaoai.agent.conversation.service.ConversationService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 对话服务实现类
 * <p>
 * 管理用户与Agent之间的多轮对话历史。核心功能：
 * <ul>
 *   <li>createConversation - 创建对话会话，分配唯一conversationCode，初始状态为active</li>
 *   <li>addMessage - 追加消息，sequenceNumber基于现有消息数量递增，同时更新对话的messageCount和lastInteractionAt</li>
 *   <li>compressConversation - 消息数>=10时调用LLM生成摘要，将旧消息标记为compressed，
 *       摘要存储在Conversation的summary字段</li>
 *   <li>branchConversation - 从指定消息节点创建对话分支，复制分支点及之前的所有消息到新对话</li>
 *   <li>rollbackToMessage - 删除指定消息之后的所有消息，用于撤销错误的对话步骤</li>
 *   <li>exportConversation - 支持Markdown、JSON、纯文本三种导出格式</li>
 * </ul>
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationMessageMapper messageMapper;
    private final ModelGateway modelGateway;

    /**
     * 构造函数
     *
     * @param messageMapper ConversationMessage数据访问层
     * @param modelGateway  模型网关，用于compressConversation时调用LLM生成摘要
     */
    @Autowired
    public ConversationServiceImpl(ConversationMessageMapper messageMapper, ModelGateway modelGateway) {
        this.messageMapper = messageMapper;
        this.modelGateway = modelGateway;
    }

    /**
     * 创建新对话
     * <p>
     * 初始化messageCount=0、totalTokens=0、status=active。
     * conversationCode使用UUID生成唯一标识。
     * lastInteractionAt、createdAt、updatedAt均设为当前时间。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param agentId  Agent ID
     * @param title    对话标题（可选）
     * @return 创建后的Conversation实体
     */
    @Override
public Conversation createConversation(Long tenantId, Long userId, Long agentId, String title) {
        Conversation conversation = new Conversation();
        conversation.setTenantId(tenantId);
        conversation.setUserId(userId);
        conversation.setAgentId(agentId);
        conversation.setTitle(title);
        conversation.setConversationCode(UUID.randomUUID().toString());
        conversation.setStatus("active");
        conversation.setMessageCount(0);
        conversation.setTotalTokens(0L);
        conversation.setCreatedAt(OffsetDateTime.now());
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversation.setLastInteractionAt(OffsetDateTime.now());

        save(conversation);
        log.info("Created conversation: id={}, userId={}, agentId={}", conversation.getId(), userId, agentId);

        return conversation;
    }

    /**
     * 向对话追加消息
     * <p>
     * sequenceNumber通过查询当前消息总数+1计算，保证消息顺序唯一且递增。
     * 消息初始compressed=false（未压缩）。
     * 同时更新对话的messageCount和lastInteractionAt时间戳。
     * 对话不存在时抛出IllegalArgumentException。
     * </p>
     *
     * @param conversationId 对话ID
     * @param role           消息角色（user/assistant/system）
     * @param content        消息内容
     * @param taskId         关联的任务ID（可选，可为null）
     * @return 创建后的ConversationMessage实体
     */
    @Override
public ConversationMessage addMessage(Long conversationId, String role, String content, Long taskId) {
        Conversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        // 获取当前最大序号
        Integer maxSeq = messageMapper.selectCount(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId));

        ConversationMessage message = new ConversationMessage();
        message.setTenantId(conversation.getTenantId());
        message.setConversationId(conversationId);
        message.setMessageCode(UUID.randomUUID().toString());
        message.setRole(role);
        message.setContent(content);
        message.setSequenceNumber(maxSeq + 1);
        message.setTaskId(taskId);
        message.setCompressed(false);
        message.setCreatedAt(OffsetDateTime.now());
        message.setUpdatedAt(OffsetDateTime.now());

        messageMapper.insert(message);

        // 更新对话统计
        conversation.setMessageCount(conversation.getMessageCount() + 1);
        conversation.setLastInteractionAt(OffsetDateTime.now());
        conversation.setUpdatedAt(OffsetDateTime.now());
        updateById(conversation);

        log.debug("Added message to conversation: conversationId={}, role={}, seq={}",
                conversationId, role, message.getSequenceNumber());

        return message;
    }

    /**
     * 获取对话的所有消息，按sequenceNumber升序排列（即发送时间顺序）
     *
     * @param conversationId 对话ID
     * @return 消息列表
     */
    @Override
public List<ConversationMessage> getMessages(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByAsc(ConversationMessage::getSequenceNumber));
    }

    /**
     * 压缩对话历史
     * <p>
     * 当消息数>=10时，调用LLM生成中文摘要，存储在Conversation的summary字段，
     * 并将所有旧消息的compressed标记为true（compressedContent保存原始内容）。
     * 消息数<10时返回null（不执行压缩）。
     * LLM调用失败时记录错误日志并返回null，不向外抛出异常。
     * </p>
     *
     * @param conversationId 对话ID
     * @return 摘要文本，消息过少或压缩失败时返回null
     */
    @Override
public String compressConversation(Long conversationId) {
        List<ConversationMessage> messages = getMessages(conversationId);

        if (messages.size() < 10) {
            log.info("Conversation too short to compress: conversationId={}, messageCount={}",
                    conversationId, messages.size());
            return null;
        }

        // 构建压缩提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下对话生成一个简洁的摘要，保留关键信息：\n\n");

        for (ConversationMessage msg : messages) {
            prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        prompt.append("\n请生成摘要：");

        try {
            // 调用模型生成摘要
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L); // 使用默认模型
            command.setPrompt(prompt.toString());

            ChatModelResponse response = modelGateway.chat(command);
            String summary = response.getContent().trim();

            // 更新对话摘要
            Conversation conversation = getById(conversationId);
            conversation.setSummary(summary);
            conversation.setUpdatedAt(OffsetDateTime.now());
            updateById(conversation);

            // 标记旧消息为已压缩
            for (ConversationMessage msg : messages) {
                msg.setCompressed(true);
                msg.setCompressedContent(msg.getContent());
                msg.setUpdatedAt(OffsetDateTime.now());
                messageMapper.updateById(msg);
            }

            log.info("Compressed conversation: conversationId={}, messageCount={}, summaryLength={}",
                    conversationId, messages.size(), summary.length());

            return summary;

        } catch (Exception e) {
            log.error("Failed to compress conversation: conversationId={}", conversationId, e);
            return null;
        }
    }

    /**
     * 搜索对话
     * <p>
     * 在租户的active对话中，按keyword在title和summary中模糊匹配。
     * 结果按lastInteractionAt倒序排列（最近活跃的对话优先），限制返回数量为limit条。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param keyword  搜索关键词（可选，null时返回全部active对话）
     * @param limit    最大返回数量
     * @return 匹配的对话列表
     */
    @Override
public List<Conversation> searchConversations(Long tenantId, Long userId, String keyword, int limit) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getTenantId, tenantId)
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, "active")
                .orderByDesc(Conversation::getLastInteractionAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Conversation::getTitle, keyword)
                    .or().like(Conversation::getSummary, keyword)
            );
        }

        wrapper.last("limit " + limit);

        return list(wrapper);
    }

    /**
     * 从对话的某个消息节点创建分支
     * <p>
     * 执行流程：
     * <ol>
     *   <li>验证原对话和分支点消息是否存在，且消息属于该对话</li>
     *   <li>创建新对话（标题默认追加" (分支)"后缀），记录parentConversationId和branchPointMessageId</li>
     *   <li>复制原对话中sequenceNumber <= 分支点的所有消息到新对话（重新生成messageCode，compressed=false）</li>
     *   <li>更新新对话的messageCount为复制的消息数量</li>
     * </ol>
     * </p>
     *
     * @param conversationId       原对话ID
     * @param branchPointMessageId 分支起点消息ID（包含该消息）
     * @param newTitle             新对话标题（null时使用原标题加" (分支)"后缀）
     * @return 新创建的分支Conversation实体
     * @throws IllegalArgumentException 如果对话不存在或分支点消息无效
     */
    @Override
public Conversation branchConversation(Long conversationId, Long branchPointMessageId, String newTitle) {
        Conversation original = getById(conversationId);
        if (original == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        ConversationMessage branchPoint = messageMapper.selectById(branchPointMessageId);
        if (branchPoint == null || !branchPoint.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException("Invalid branch point message: " + branchPointMessageId);
        }

        // 创建新对话
        Conversation branched = createConversation(
                original.getTenantId(),
                original.getUserId(),
                original.getAgentId(),
                newTitle != null ? newTitle : original.getTitle() + " (分支)"
        );

        branched.setParentConversationId(conversationId);
        branched.setBranchPointMessageId(branchPointMessageId);
        updateById(branched);

        // 复制分支点之前的消息
        List<ConversationMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .le(ConversationMessage::getSequenceNumber, branchPoint.getSequenceNumber())
                        .orderByAsc(ConversationMessage::getSequenceNumber)
        );

        for (ConversationMessage msg : messages) {
            ConversationMessage newMsg = new ConversationMessage();
            newMsg.setTenantId(msg.getTenantId());
            newMsg.setConversationId(branched.getId());
            newMsg.setMessageCode(UUID.randomUUID().toString());
            newMsg.setRole(msg.getRole());
            newMsg.setContent(msg.getContent());
            newMsg.setSequenceNumber(msg.getSequenceNumber());
            newMsg.setTaskId(msg.getTaskId());
            newMsg.setCompressed(false);
            newMsg.setCreatedAt(OffsetDateTime.now());
            newMsg.setUpdatedAt(OffsetDateTime.now());
            messageMapper.insert(newMsg);
        }

        branched.setMessageCount(messages.size());
        updateById(branched);

        log.info("Branched conversation: originalId={}, branchedId={}, branchPoint={}",
                conversationId, branched.getId(), branchPointMessageId);

        return branched;
    }

    /**
     * 回退对话到指定消息
     * <p>
     * 删除sequenceNumber > 目标消息sequenceNumber的所有消息（目标消息本身保留）。
     * 将对话的messageCount更新为目标消息的sequenceNumber值。
     * 用于撤销错误的对话步骤或重新探索不同的对话路径。
     * </p>
     *
     * @param conversationId 对话ID
     * @param messageId      回退目标消息ID（保留该消息）
     * @throws IllegalArgumentException 如果消息不存在或不属于该对话
     */
    @Override
public void rollbackToMessage(Long conversationId, Long messageId) {
        ConversationMessage targetMessage = messageMapper.selectById(messageId);
        if (targetMessage == null || !targetMessage.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException("Invalid message: " + messageId);
        }

        // 删除目标消息之后的所有消息
        messageMapper.delete(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .gt(ConversationMessage::getSequenceNumber, targetMessage.getSequenceNumber()));

        // 更新对话统计
        Conversation conversation = getById(conversationId);
        conversation.setMessageCount(targetMessage.getSequenceNumber());
        conversation.setUpdatedAt(OffsetDateTime.now());
        updateById(conversation);

        log.info("Rolled back conversation: conversationId={}, toMessage={}, newMessageCount={}",
                conversationId, messageId, conversation.getMessageCount());
    }

    /**
     * 导出对话为指定格式的字符串。
     * 支持"markdown"（含标题、元数据、摘要、对话内容）、
     * "json"（简化格式）和纯文本（默认）三种格式。
     *
     * @param conversationId 对话ID
     * @param format         导出格式："markdown"、"json"或其他（默认纯文本）
     * @return 格式化后的对话字符串
     * @throws IllegalArgumentException 如果对话不存在
     */
    @Override
public String exportConversation(Long conversationId, String format) {
        Conversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        List<ConversationMessage> messages = getMessages(conversationId);

        if ("markdown".equalsIgnoreCase(format)) {
            return exportAsMarkdown(conversation, messages);
        } else if ("json".equalsIgnoreCase(format)) {
            return exportAsJson(conversation, messages);
        } else {
            return exportAsText(conversation, messages);
        }
    }

    /**
     * 导出为Markdown格式：含#标题、创建时间、消息数、摘要（若有）和##对话内容（按角色分节）
     */
    private String exportAsMarkdown(Conversation conversation, List<ConversationMessage> messages) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(conversation.getTitle()).append("\n\n");
        md.append("**创建时间**: ").append(conversation.getCreatedAt()).append("\n");
        md.append("**消息数**: ").append(conversation.getMessageCount()).append("\n\n");

        if (StringUtils.hasText(conversation.getSummary())) {
            md.append("## 摘要\n\n");
            md.append(conversation.getSummary()).append("\n\n");
        }

        md.append("## 对话内容\n\n");

        for (ConversationMessage msg : messages) {
            md.append("### ").append(msg.getRole().substring(0, 1).toUpperCase() + msg.getRole().substring(1))
                    .append("\n\n");
            md.append(msg.getContent()).append("\n\n");
        }

        return md.toString();
    }

    /**
     * 导出为JSON格式（简化实现，仅含title和messageCount，messages内容省略）
     */
    private String exportAsJson(Conversation conversation, List<ConversationMessage> messages) {
        // 简化实现，实际应该使用 Jackson
        return String.format("{\"title\":\"%s\",\"messageCount\":%d,\"messages\":[...]}",
                conversation.getTitle(), conversation.getMessageCount());
    }

    /**
     * 导出为纯文本格式：含对话标题、创建时间、消息数，每条消息以[role] content格式展示
     */
    private String exportAsText(Conversation conversation, List<ConversationMessage> messages) {
        StringBuilder text = new StringBuilder();
        text.append("对话: ").append(conversation.getTitle()).append("\n");
        text.append("创建时间: ").append(conversation.getCreatedAt()).append("\n");
        text.append("消息数: ").append(conversation.getMessageCount()).append("\n\n");

        for (ConversationMessage msg : messages) {
            text.append("[").append(msg.getRole()).append("] ")
                    .append(msg.getContent()).append("\n\n");
        }

        return text.toString();
    }

    /**
     * 归档对话，将status从active改为archived，更新updatedAt时间戳。
     * 归档后对话不再出现在searchConversations的active过滤结果中。
     *
     * @param conversationId 对话ID
     * @throws IllegalArgumentException 如果对话不存在
     */
    @Override
public void archiveConversation(Long conversationId) {
        Conversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        conversation.setStatus("archived");
        conversation.setUpdatedAt(OffsetDateTime.now());
        updateById(conversation);

        log.info("Archived conversation: id={}", conversationId);
    }

    /**
     * 获取对话统计信息
     * <p>
     * 遍历所有消息计算：
     * <ul>
     *   <li>messageCount - 消息总数（messages.size()）</li>
     *   <li>userMessageCount - role=user的消息数</li>
     *   <li>assistantMessageCount - role=assistant的消息数</li>
     *   <li>totalTokens - 所有消息tokenCount之和（null值跳过）</li>
     * </ul>
     * </p>
     *
     * @param conversationId 对话ID
     * @return ConversationStats统计对象
     */
    @Override
public ConversationStats getStats(Long conversationId) {
        List<ConversationMessage> messages = getMessages(conversationId);

        ConversationStats stats = new ConversationStats();
        stats.messageCount = messages.size();
        stats.userMessageCount = (int) messages.stream().filter(m -> "user".equals(m.getRole())).count();
        stats.assistantMessageCount = (int) messages.stream().filter(m -> "assistant".equals(m.getRole())).count();
        stats.totalTokens = messages.stream()
                .filter(m -> m.getTokenCount() != null)
                .mapToLong(ConversationMessage::getTokenCount)
                .sum();

        return stats;
    }
}
