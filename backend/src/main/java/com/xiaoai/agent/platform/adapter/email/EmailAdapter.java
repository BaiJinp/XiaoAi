package com.xiaoai.agent.platform.adapter.email;

import com.xiaoai.agent.platform.PlatformAdapter;
import com.xiaoai.agent.platform.PlatformMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class EmailAdapter implements PlatformAdapter {

    public static final String PLATFORM_TYPE = "email";

    private final EmailConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, String> userInfoCache = new ConcurrentHashMap<>();
    private PlatformMessageHandler messageHandler;

    public EmailAdapter(EmailConfig config) {
        this.config = config;
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void start() {
        if (!config.isEnabled()) {
            log.info("Email adapter is disabled");
            return;
        }
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean sendMessage(String chatId, String message) {
        log.info("Email send requested: chatId={}", chatId);
        return config.isEnabled();
    }

    @Override
    public boolean sendMarkdownMessage(String chatId, String message) {
        return sendMessage(chatId, message);
    }

    @Override
    public boolean replyMessage(String chatId, String replyToMessageId, String message) {
        return sendMessage(chatId, message);
    }

    @Override
    public void setMessageHandler(PlatformMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getUserInfo(String userId) {
        return userInfoCache.computeIfAbsent(userId, id -> "{\"email\":\"" + id + "\"}");
    }

    @Override
    public String getChatInfo(String chatId) {
        return getUserInfo(chatId);
    }
}
