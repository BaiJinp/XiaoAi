package com.xiaoai.agent.platform;

/**
 * 平台消息处理器接口
 * 处理从平台接收到的消息
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface PlatformMessageHandler {

    /**
     * 处理接收到的消息
     *
     * @param message 平台消息
     */
    void handleMessage(PlatformMessage message);

    /**
     * 处理错误
     *
     * @param platformType 平台类型
     * @param error 错误信息
     */
    void handleError(String platformType, Exception error);

    /**
     * 处理适配器状态变化
     *
     * @param platformType 平台类型
     * @param running 是否运行中
     */
    void handleStatusChange(String platformType, boolean running);
}
