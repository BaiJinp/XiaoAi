package com.xiaoai.agent.platform.adapter.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.platform.PlatformAdapter;
import com.xiaoai.agent.platform.PlatformMessage;
import com.xiaoai.agent.platform.PlatformMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Webhook平台适配器
 * <p>
 * 接收HTTP POST回调，将结果发送到回调URL
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Slf4j
@Component
public class WebhookAdapter implements PlatformAdapter {

    public static final String PLATFORM_TYPE = "webhook";

    private final WebhookConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlatformMessageHandler messageHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 缓存的用户信息
     */
    private final Map<String, String> userInfoCache = new ConcurrentHashMap<>();

    /**
     * 缓存的群组/会话信息
     */
    private final Map<String, String> chatInfoCache = new ConcurrentHashMap<>();

    /**
     * 存储回调URL映射，key: sessionId, value: callbackUrl
     */
    private final Map<String, String> callbackUrlMap = new ConcurrentHashMap<>();

    public WebhookAdapter(WebhookConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .build();
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void start() throws Exception {
        if (running.get()) {
            log.warn("WebhookAdapter is already running");
            return;
        }

        if (!config.isEnabled()) {
            log.warn("WebhookAdapter is disabled in configuration");
            return;
        }

        try {
            running.set(true);
            log.info("WebhookAdapter started successfully");

            if (messageHandler != null) {
                messageHandler.handleStatusChange(PLATFORM_TYPE, true);
            }
        } catch (Exception e) {
            log.error("Failed to start WebhookAdapter", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!running.get()) {
            return;
        }

        running.set(false);
        userInfoCache.clear();
        chatInfoCache.clear();
        callbackUrlMap.clear();

        log.info("WebhookAdapter stopped");

        if (messageHandler != null) {
            messageHandler.handleStatusChange(PLATFORM_TYPE, false);
        }
    }

    @Override
    public boolean sendMessage(String chatId, String message) {
        if (!validateState()) {
            return false;
        }

        String callbackUrl = callbackUrlMap.get(chatId);
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("No callback URL registered for session: {}", chatId);
            return false;
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("sessionId", chatId);
            requestBody.put("content", message);
            requestBody.put("timestamp", System.currentTimeMillis());

            // 如果有密钥，添加签名
            if (config.getSecret() != null && !config.getSecret().isEmpty()) {
                String signature = generateSignature(requestBody.toString(), config.getSecret());
                requestBody.put("signature", signature);
            }

            var response = restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            log.debug("Webhook response for session {}: status={}", chatId, response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Error sending message to webhook for session: {}", chatId, e);
            handleError(e);
            return false;
        }
    }

    @Override
    public boolean sendMarkdownMessage(String chatId, String message) {
        // Webhook场景下，markdown和普通文本都作为字符串发送
        return sendMessage(chatId, message);
    }

    @Override
    public boolean replyMessage(String chatId, String replyToMessageId, String message) {
        if (!validateState()) {
            return false;
        }

        String callbackUrl = callbackUrlMap.get(chatId);
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("No callback URL registered for session: {}", chatId);
            return false;
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("sessionId", chatId);
            requestBody.put("replyToMessageId", replyToMessageId);
            requestBody.put("content", message);
            requestBody.put("timestamp", System.currentTimeMillis());
            requestBody.put("isReply", true);

            // 如果有密钥，添加签名
            if (config.getSecret() != null && !config.getSecret().isEmpty()) {
                String signature = generateSignature(requestBody.toString(), config.getSecret());
                requestBody.put("signature", signature);
            }

            var response = restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            log.debug("Webhook reply response for session {}: status={}", chatId, response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Error sending reply message to webhook for session: {}", chatId, e);
            handleError(e);
            return false;
        }
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
        if (!validateState()) {
            return null;
        }

        if (userInfoCache.containsKey(userId)) {
            return userInfoCache.get(userId);
        }

        // Webhook场景下构造简单用户信息
        String userInfo = "{\"userId\": \"" + userId + "\"}";
        userInfoCache.put(userId, userInfo);
        return userInfo;
    }

    @Override
    public String getChatInfo(String chatId) {
        if (!validateState()) {
            return null;
        }

        if (chatInfoCache.containsKey(chatId)) {
            return chatInfoCache.get(chatId);
        }

        // 获取关联的回调URL
        String callbackUrl = callbackUrlMap.get(chatId);
        String chatInfo = "{\"sessionId\": \"" + chatId + "\", \"callbackUrl\": \"" + callbackUrl + "\"}";
        chatInfoCache.put(chatId, chatInfo);
        return chatInfo;
    }

    /**
     * 处理传入的Webhook回调
     *
     * @param sessionId   会话ID
     * @param callbackUrl 回调URL
     * @param payload     请求体
     * @return 是否处理成功
     */
    public boolean handleIncomingWebhook(String sessionId, String callbackUrl, String payload) {
        if (!running.get()) {
            log.warn("WebhookAdapter is not running, ignoring incoming webhook");
            return false;
        }

        try {
            // 验证签名（如果配置了密钥）
            if (config.getSecret() != null && !config.getSecret().isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(payload);
                String receivedSignature = jsonNode.has("signature") ? jsonNode.get("signature").asText("") : "";

                // 移除signature字段后计算原始内容的签名
                ((ObjectNode) jsonNode).remove("signature");
                String contentToVerify = jsonNode.toString();
                String expectedSignature = generateSignature(contentToVerify, config.getSecret());

                if (!expectedSignature.equals(receivedSignature)) {
                    log.warn("Invalid webhook signature for session: {}", sessionId);
                    return false;
                }
            }

            // 注册回调URL
            if (callbackUrl != null && !callbackUrl.isEmpty()) {
                callbackUrlMap.put(sessionId, callbackUrl);
            }

            // 构建PlatformMessage
            PlatformMessage message = buildPlatformMessageFromPayload(sessionId, payload);

            if (messageHandler != null && message != null) {
                messageHandler.handleMessage(message);
                return true;
            }

            return message != null;
        } catch (Exception e) {
            log.error("Error handling incoming webhook for session: {}", sessionId, e);
            handleError(e);
            return false;
        }
    }

    /**
     * 从载荷构建PlatformMessage
     */
    private PlatformMessage buildPlatformMessageFromPayload(String sessionId, String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            String content = jsonNode.has("content") ? jsonNode.get("content").asText() : "";
            String userId = jsonNode.has("userId") ? jsonNode.get("userId").asText() : sessionId;
            String messageId = jsonNode.has("messageId") ? jsonNode.get("messageId").asText() : generateMessageId(sessionId);

            return PlatformMessage.builder()
                    .messageId(messageId)
                    .platformType(PLATFORM_TYPE)
                    .platformUserId(userId)
                    .platformChatId(sessionId)
                    .content(content)
                    .messageType("text")
                    .isGroup(false)
                    .rawData(objectMapper.convertValue(jsonNode, Map.class))
                    .build();
        } catch (Exception e) {
            log.error("Error building PlatformMessage from payload", e);
            return null;
        }
    }

    /**
     * 生成消息ID
     */
    private String generateMessageId(String sessionId) {
        return sessionId + "-" + System.currentTimeMillis();
    }

    /**
     * 生成签名
     */
    private String generateSignature(String content, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] signData = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    /**
     * 验证适配器状态
     */
    private boolean validateState() {
        if (!running.get()) {
            log.warn("WebhookAdapter is not running");
            return false;
        }
        if (!config.isEnabled()) {
            log.warn("WebhookAdapter is disabled");
            return false;
        }
        return true;
    }

    /**
     * 统一错误处理
     */
    private void handleError(Exception e) {
        if (messageHandler != null) {
            messageHandler.handleError(PLATFORM_TYPE, e);
        }
    }
}
