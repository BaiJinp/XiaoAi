package com.xiaoai.agent.conversation.compression;

import com.xiaoai.agent.conversation.entity.Conversation;
import com.xiaoai.agent.conversation.entity.ConversationMessage;
import com.xiaoai.agent.conversation.service.ConversationService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话压缩服务
 * <p>
 * 提供智能对话压缩功能，用于控制LLM上下文长度。核心机制：
 * <ul>
 *   <li>上下文限制：DEFAULT_CONTEXT_LIMIT=8000 tokens</li>
 *   <li>压缩阈值：当前token数达到限制的80%时触发自动压缩</li>
 *   <li>压缩策略：保留最近的消息（最多20条或总消息数的一半），
 *       将旧消息通过LLM生成摘要，摘要作为system消息插入，旧消息标记为compressed=true</li>
 *   <li>降级方案：LLM调用失败时，使用simpleCompress截取前5条消息的摘要</li>
 * </ul>
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class ConversationCompressionService {

    private static final Logger log = LoggerFactory.getLogger(ConversationCompressionService.class);

    /**
     * 默认上下文长度限制（token数），超过此值需要压缩对话历史
     */
    private static final int DEFAULT_CONTEXT_LIMIT = 8000;

    /**
     * 压缩触发阈值（80%），当前token数超过限制的80%时自动触发压缩
     */
    private static final double COMPRESSION_THRESHOLD = 0.8;

    private final ConversationService conversationService;
    private final ModelGateway modelGateway;

    /**
     * 构造函数
     *
     * @param conversationService 对话服务，用于获取/更新对话和消息
     * @param modelGateway        模型网关，用于调用LLM生成对话摘要
     */
    @Autowired
    public ConversationCompressionService(ConversationService conversationService,
                                          ModelGateway modelGateway) {
        this.conversationService = conversationService;
        this.modelGateway = modelGateway;
    }

    /**
     * 检查对话是否需要压缩
     * <p>
     * 计算当前对话所有消息的token总数，若超过DEFAULT_CONTEXT_LIMIT * COMPRESSION_THRESHOLD（6400 tokens）
     * 则返回true，表示应触发自动压缩。对话不存在时返回false。
     * </p>
     *
     * @param conversationId 对话ID
     * @return 是否需要压缩
     */
    public boolean needsCompression(Long conversationId) {
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            return false;
        }

        // 计算当前 token 使用量
        long currentTokens = calculateCurrentTokens(conversationId);

        // 检查是否超过阈值
        return currentTokens > DEFAULT_CONTEXT_LIMIT * COMPRESSION_THRESHOLD;
    }

    /**
     * 计算对话当前的token总数（仅统计tokenCount非null的消息）
     *
     * @param conversationId 对话ID
     * @return token总数
     */
    public long calculateCurrentTokens(Long conversationId) {
        List<ConversationMessage> messages = conversationService.getMessages(conversationId);

        return messages.stream()
                .filter(m -> m.getTokenCount() != null)
                .mapToLong(ConversationMessage::getTokenCount)
                .sum();
    }

    /**
     * 自动压缩对话
     * <p>
     * 先通过needsCompression检查是否需要压缩，若未超过阈值则返回compressed=false的结果。
     * 超过阈值时调用compressConversation执行实际压缩。
     * </p>
     *
     * @param conversationId 对话ID
     * @return CompressionResult，包含是否执行了压缩及token变化量
     */
    public CompressionResult autoCompress(Long conversationId) {
        if (!needsCompression(conversationId)) {
            return new CompressionResult(false, 0, 0, "No compression needed");
        }

        return compressConversation(conversationId);
    }

    /**
     * 执行对话压缩
     * <p>
     * 压缩策略：
     * <ol>
     *   <li>消息数<10时不执行压缩，直接返回结果</li>
     *   <li>将消息分为旧消息和近期消息：保留最近min(20, totalMessages/2)条消息不压缩</li>
     *   <li>对旧消息调用compressMessages生成LLM摘要</li>
     *   <li>将摘要作为system角色的新消息插入对话，并标记旧消息为compressed=true</li>
     *   <li>计算压缩前后的token数差异，返回CompressionResult</li>
     * </ol>
     * </p>
     *
     * @param conversationId 对话ID
     * @return CompressionResult，包含压缩结果和token节省量
     * @throws IllegalArgumentException 如果对话不存在
     */
    public CompressionResult compressConversation(Long conversationId) {
        log.info("Compressing conversation: id={}", conversationId);

        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        List<ConversationMessage> messages = conversationService.getMessages(conversationId);

        if (messages.size() < 10) {
            return new CompressionResult(false, 0, 0, "Conversation too short to compress");
        }

        // 计算压缩前的 token 数
        long tokensBefore = calculateCurrentTokens(conversationId);

        // 分离消息：保留最近的消息，压缩旧消息
        int keepRecentCount = Math.min(20, messages.size() / 2);
        List<ConversationMessage> recentMessages = messages.subList(
                messages.size() - keepRecentCount, messages.size());
        List<ConversationMessage> oldMessages = messages.subList(
                0, messages.size() - keepRecentCount);

        // 压缩旧消息
        String compressedSummary = compressMessages(oldMessages);

        // 创建压缩摘要消息
        ConversationMessage summaryMessage = new ConversationMessage();
        summaryMessage.setConversationId(conversationId);
        summaryMessage.setRole("system");
        summaryMessage.setContent("[Previous conversation summary]\n" + compressedSummary);
        summaryMessage.setSequenceNumber(messages.size() + 1);
        summaryMessage.setCompressed(true);
        summaryMessage.setTokenCount(estimateTokens(compressedSummary));

        // 保存压缩摘要
        conversationService.addMessage(summaryMessage);

        // 标记旧消息为已压缩
        for (ConversationMessage msg : oldMessages) {
            if (!msg.getCompressed()) {
                msg.setCompressed(true);
                conversationService.updateMessage(msg);
            }
        }

        // 计算压缩后的 token 数
        long tokensAfter = calculateCurrentTokens(conversationId);
        long tokensSaved = tokensBefore - tokensAfter;

        log.info("Conversation compressed: id={}, before={}, after={}, saved={}",
                conversationId, tokensBefore, tokensAfter, tokensSaved);

        return new CompressionResult(true, tokensBefore, tokensAfter,
                "Compressed successfully");
    }

    /**
     * 调用LLM对消息列表生成英文摘要（2-3段），LLM调用失败时降级为simpleCompress。
     *
     * @param messages 待压缩的消息列表
     * @return 摘要文本
     */
    private String compressMessages(List<ConversationMessage> messages) {
        if (messages.isEmpty()) {
            return "";
        }

        // 构建压缩提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please summarize the following conversation, keeping only the key information:\n\n");

        for (ConversationMessage msg : messages) {
            prompt.append("[").append(msg.getRole()).append("]: ")
                    .append(msg.getContent()).append("\n");
        }

        prompt.append("\nProvide a concise summary in 2-3 paragraphs:");

        try {
            // 调用模型生成摘要
            ChatModelCommand command = new ChatModelCommand();
            command.setModelId(1L); // 使用默认模型
            command.setPrompt(prompt.toString());

            ChatModelResponse response = modelGateway.chat(command);
            return response.getContent();

        } catch (Exception e) {
            log.error("Failed to compress messages", e);
            // 降级处理：简单截取
            return simpleCompress(messages);
        }
    }

    /**
     * 降级压缩方案（LLM不可用时使用）
     * <p>
     * 截取前5条消息的内容（每条最长100字符），以列表形式输出摘要，
     * 超过5条时追加"... and N more messages"提示。
     * </p>
     *
     * @param messages 待压缩的消息列表
     * @return 简化的摘要文本
     */
    private String simpleCompress(List<ConversationMessage> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("Summary of ").append(messages.size()).append(" messages:\n");

        // 提取关键信息
        for (int i = 0; i < Math.min(5, messages.size()); i++) {
            ConversationMessage msg = messages.get(i);
            String content = msg.getContent();
            if (content.length() > 100) {
                content = content.substring(0, 100) + "...";
            }
            summary.append("- [").append(msg.getRole()).append("]: ")
                    .append(content).append("\n");
        }

        if (messages.size() > 5) {
            summary.append("- ... and ").append(messages.size() - 5)
                    .append(" more messages\n");
        }

        return summary.toString();
    }

    /**
     * 粗略估算文本token数量，按1 token ≈ 4字符的比例计算。
     * 空文本返回0。
     *
     * @param text 待估算文本
     * @return 估算的token数
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 粗略估算：1 token ≈ 4 字符
        return text.length() / 4;
    }

    /**
     * 获取对话的压缩状态概览
     * <p>
     * 统计当前token数、是否需要压缩、已压缩消息数和消息总数，
     * 用于前端展示对话的token使用情况和压缩状态。
     * </p>
     *
     * @param conversationId 对话ID
     * @return CompressionStatus对象
     * @throws IllegalArgumentException 如果对话不存在
     */
    public CompressionStatus getCompressionStatus(Long conversationId) {
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        long currentTokens = calculateCurrentTokens(conversationId);
        List<ConversationMessage> messages = conversationService.getMessages(conversationId);
        long compressedCount = messages.stream()
                .filter(ConversationMessage::getCompressed)
                .count();

        return new CompressionStatus(
                currentTokens,
                DEFAULT_CONTEXT_LIMIT,
                currentTokens > DEFAULT_CONTEXT_LIMIT * COMPRESSION_THRESHOLD,
                compressedCount,
                messages.size()
        );
    }

    /**
     * 压缩操作的结果对象
     * <p>
     * 记录本次压缩是否执行、压缩前后的token数及操作消息。
     * tokensSaved通过tokensBefore - tokensAfter计算得出。
     * </p>
     */
    public static class CompressionResult {
        private final boolean compressed;
        private final long tokensBefore;
        private final long tokensAfter;
        private final String message;

        public CompressionResult(boolean compressed, long tokensBefore, long tokensAfter, String message) {
            this.compressed = compressed;
            this.tokensBefore = tokensBefore;
            this.tokensAfter = tokensAfter;
            this.message = message;
        }
public boolean isCompressed() { return compressed; }
public long getTokensBefore() { return tokensBefore; }
public long getTokensAfter() { return tokensAfter; }
public long getTokensSaved() { return tokensBefore - tokensAfter; }
public String getMessage() { return message; }
    }

    /**
     * 对话压缩状态快照
     * <p>
     * 记录当前对话的token使用情况和压缩进度，用于前端展示和决策。
     * usagePercent通过(currentTokens / tokenLimit * 100)计算，反映上下文占用百分比。
     * </p>
     */
    public static class CompressionStatus {
        private final long currentTokens;
        private final long tokenLimit;
        private final boolean needsCompression;
        private final long compressedMessages;
        private final long totalMessages;

        public CompressionStatus(long currentTokens, long tokenLimit, boolean needsCompression,
                                long compressedMessages, long totalMessages) {
            this.currentTokens = currentTokens;
            this.tokenLimit = tokenLimit;
            this.needsCompression = needsCompression;
            this.compressedMessages = compressedMessages;
            this.totalMessages = totalMessages;
        }
public long getCurrentTokens() { return currentTokens; }
public long getTokenLimit() { return tokenLimit; }
public boolean isNeedsCompression() { return needsCompression; }
public long getCompressedMessages() { return compressedMessages; }
public long getTotalMessages() { return totalMessages; }
public double getUsagePercent() { return (double) currentTokens / tokenLimit * 100; }
    }
}
