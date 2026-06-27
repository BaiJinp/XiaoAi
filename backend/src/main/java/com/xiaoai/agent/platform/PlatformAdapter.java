package com.xiaoai.agent.platform;

/**
 * 平台适配器接口
 * 不同平台（Telegram、Discord、Slack等）需要实现此接口
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface PlatformAdapter {

    /**
     * 获取平台类型
     */
    String getPlatformType();

    /**
     * 启动适配器
     */
    void start() throws Exception;

    /**
     * 停止适配器
     */
    void stop() throws Exception;

    /**
     * 发送消息到平台
     *
     * @param chatId 会话ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean sendMessage(String chatId, String message);

    /**
     * 发送富文本消息
     *
     * @param chatId 会话ID
     * @param message 消息内容（Markdown格式）
     * @return 是否发送成功
     */
    boolean sendMarkdownMessage(String chatId, String message);

    /**
     * 回复消息
     *
     * @param chatId 会话ID
     * @param replyToMessageId 回复的消息ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean replyMessage(String chatId, String replyToMessageId, String message);

    /**
     * 设置消息处理器
     */
    void setMessageHandler(PlatformMessageHandler handler);

    /**
     * 检查适配器是否正在运行
     */
    boolean isRunning();

    /**
     * 获取平台用户信息
     *
     * @param userId 平台用户ID
     * @return 用户信息（JSON格式）
     */
    String getUserInfo(String userId);

    /**
     * 获取平台群组信息
     *
     * @param chatId 群组ID
     * @return 群组信息（JSON格式）
     */
    String getChatInfo(String chatId);
}
