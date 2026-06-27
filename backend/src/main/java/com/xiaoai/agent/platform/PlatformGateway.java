package com.xiaoai.agent.platform;

import com.xiaoai.agent.conversation.service.ConversationService;
import com.xiaoai.agent.runtime.gateway.RuntimeGateway;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.user.identity.UserIdentityMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台消息网关
 * 管理所有平台适配器，统一处理消息
 */
@Component
public class PlatformGateway implements PlatformMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformGateway.class);

    private final Map<String, PlatformAdapter> adapters = new ConcurrentHashMap<>();
    private final UserIdentityMappingService userIdentityMappingService;
    private final ConversationService conversationService;
    private final RuntimeGateway runtimeGateway;

    @Autowired
    public PlatformGateway(List<PlatformAdapter> adapterList,
                           UserIdentityMappingService userIdentityMappingService,
                           ConversationService conversationService,
                           RuntimeGateway runtimeGateway) {
        this.userIdentityMappingService = userIdentityMappingService;
        this.conversationService = conversationService;
        this.runtimeGateway = runtimeGateway;

        // 注册所有适配器
        for (PlatformAdapter adapter : adapterList) {
            adapters.put(adapter.getPlatformType(), adapter);
            adapter.setMessageHandler(this);
            log.info("Registered platform adapter: {}", adapter.getPlatformType());
        }
    }

    /**
     * 启动所有适配器
     */
    public void startAll() {
        for (PlatformAdapter adapter : adapters.values()) {
            try {
                adapter.start();
                log.info("Started platform adapter: {}", adapter.getPlatformType());
            } catch (Exception e) {
                log.error("Failed to start platform adapter: {}", adapter.getPlatformType(), e);
            }
        }
    }

    /**
     * 停止所有适配器
     */
    public void stopAll() {
        for (PlatformAdapter adapter : adapters.values()) {
            try {
                adapter.stop();
                log.info("Stopped platform adapter: {}", adapter.getPlatformType());
            } catch (Exception e) {
                log.error("Failed to stop platform adapter: {}", adapter.getPlatformType(), e);
            }
        }
    }

    /**
     * 启动指定平台的适配器
     */
    public void startAdapter(String platformType) throws Exception {
        PlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            throw new IllegalArgumentException("Platform not found: " + platformType);
        }
        adapter.start();
        log.info("Started platform adapter: {}", platformType);
    }

    /**
     * 停止指定平台的适配器
     */
    public void stopAdapter(String platformType) throws Exception {
        PlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            throw new IllegalArgumentException("Platform not found: " + platformType);
        }
        adapter.stop();
        log.info("Stopped platform adapter: {}", platformType);
    }

    /**
     * 发送消息到指定平台
     */
    public boolean sendMessage(String platformType, String chatId, String message) {
        PlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null || !adapter.isRunning()) {
            log.warn("Platform adapter not available: {}", platformType);
            return false;
        }
        return adapter.sendMessage(chatId, message);
    }

    /**
     * 发送 Markdown 消息到指定平台
     */
    public boolean sendMarkdownMessage(String platformType, String chatId, String message) {
        PlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null || !adapter.isRunning()) {
            log.warn("Platform adapter not available: {}", platformType);
            return false;
        }
        return adapter.sendMarkdownMessage(chatId, message);
    }

    /**
     * 获取所有已注册的平台
     */
    public List<String> getRegisteredPlatforms() {
        return List.copyOf(adapters.keySet());
    }

    /**
     * 获取平台适配器状态
     */
    public Map<String, Boolean> getAdapterStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        for (Map.Entry<String, PlatformAdapter> entry : adapters.entrySet()) {
            status.put(entry.getKey(), entry.getValue().isRunning());
        }
        return status;
    }

    @Override
public void handleMessage(PlatformMessage message) {
        log.info("Received message from {}: {}", message.getPlatformType(), message.getContent());

        try {
            // 1. 解析用户身份
            Long platformUserId = userIdentityMappingService.getPlatformUserId(
                    100L, // TODO: 从配置获取 tenantId
                    message.getPlatformType(),
                    message.getPlatformUserId()
            );

            if (platformUserId == null) {
                log.warn("Unknown user from platform: {} - {}",
                        message.getPlatformType(), message.getPlatformUserId());
                // TODO: 发送提示消息，要求用户绑定账号
                return;
            }

            // 2. 获取或创建对话
            // TODO: 根据 platformChatId 获取或创建对话

            // 3. 添加消息到对话
            // TODO: 调用 conversationService.addMessage()

            // 4. 启动 Agent 任务
            // TODO: 调用 runtimeGateway.startRun()

            // 5. 发送响应到平台
            // TODO: 根据执行结果发送响应

        } catch (Exception e) {
            log.error("Failed to handle message from {}", message.getPlatformType(), e);
            handleError(message.getPlatformType(), e);
        }
    }

    @Override
public void handleError(String platformType, Exception error) {
        log.error("Error from platform {}: {}", platformType, error.getMessage());
        // TODO: 发送错误消息到平台
    }

    @Override
public void handleStatusChange(String platformType, boolean running) {
        log.info("Platform adapter status changed: {} - {}", platformType, running ? "running" : "stopped");
        // TODO: 更新状态，通知管理员
    }
}
