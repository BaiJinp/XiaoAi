package com.xiaoai.agent.platform.adapter.feishu;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 飞书/Lark平台适配器
 * <p>
 * 支持飞书机器人消息收发、事件回调接收
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Slf4j
@Component
public class FeishuAdapter implements PlatformAdapter {

    public static final String PLATFORM_TYPE = "feishu";

    private final FeishuConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlatformMessageHandler messageHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String tenantAccessToken;
    private long tokenExpireTime;

    /**
     * 缓存的用户信息
     */
    private final Map<String, String> userInfoCache = new ConcurrentHashMap<>();

    /**
     * 缓存的群组信息
     */
    private final Map<String, String> chatInfoCache = new ConcurrentHashMap<>();

    public FeishuAdapter(FeishuConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.getApiBaseUrl())
                .build();
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void start() throws Exception {
        if (running.get()) {
            log.warn("FeishuAdapter is already running");
            return;
        }

        if (!config.isEnabled()) {
            log.warn("FeishuAdapter is disabled in configuration");
            return;
        }

        try {
            refreshTenantAccessToken();
            running.set(true);
            log.info("FeishuAdapter started successfully, appId: {}", config.getAppId());

            if (messageHandler != null) {
                messageHandler.handleStatusChange(PLATFORM_TYPE, true);
            }
        } catch (Exception e) {
            log.error("Failed to start FeishuAdapter", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!running.get()) {
            return;
        }

        running.set(false);
        tenantAccessToken = null;
        tokenExpireTime = 0;
        userInfoCache.clear();
        chatInfoCache.clear();

        log.info("FeishuAdapter stopped");

        if (messageHandler != null) {
            messageHandler.handleStatusChange(PLATFORM_TYPE, false);
        }
    }

    @Override
    public boolean sendMessage(String chatId, String message) {
        if (!validateState()) {
            return false;
        }

        try {
            String url = config.getSendMessageUrl();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("receive_id", chatId);
            requestBody.put("msg_type", "text");

            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("text", message);
            requestBody.put("content", contentNode.toString());

            var response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getValidTenantAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            int code = responseNode.get("code").asInt(-1);

            if (code == 0) {
                log.debug("Message sent successfully to chat: {}", chatId);
                return true;
            } else {
                String msg = responseNode.get("msg").asText("unknown error");
                log.error("Failed to send message to Feishu, code: {}, msg: {}", code, msg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending message to Feishu chat: {}", chatId, e);
            handleError(e);
            return false;
        }
    }

    @Override
    public boolean sendMarkdownMessage(String chatId, String message) {
        if (!validateState()) {
            return false;
        }

        try {
            String url = config.getSendMessageUrl();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("receive_id", chatId);
            // 飞书的富文本类型为 post
            requestBody.put("msg_type", "post");

            ObjectNode contentNode = objectMapper.createObjectNode();
            ObjectNode zhCnNode = objectMapper.createObjectNode();
            ObjectNode titleNode = objectMapper.createObjectNode();
            titleNode.put("tag", "text");
            titleNode.put("text", "Message");
            ObjectNode contentArrayNode = objectMapper.createArrayNode();
            contentArrayNode.add(titleNode);

            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("tag", "text");
            textNode.put("text", message);
            contentArrayNode.add(textNode);

            zhCnNode.set("title", titleNode);
            zhCnNode.set("content", contentArrayNode);
            contentNode.set("zh_cn", zhCnNode);

            requestBody.put("content", contentNode.toString());

            var response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getValidTenantAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            int code = responseNode.get("code").asInt(-1);

            if (code == 0) {
                log.debug("Post message sent successfully to chat: {}", chatId);
                return true;
            } else {
                String msg = responseNode.get("msg").asText("unknown error");
                log.error("Failed to send post message to Feishu, code: {}, msg: {}", code, msg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending markdown message to Feishu chat: {}", chatId, e);
            handleError(e);
            return false;
        }
    }

    @Override
    public boolean replyMessage(String chatId, String replyToMessageId, String message) {
        if (!validateState()) {
            return false;
        }

        try {
            String url = config.getSendMessageUrl();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("receive_id", chatId);
            requestBody.put("msg_type", "text");

            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("text", message);
            requestBody.put("content", contentNode.toString());

            // 设置回复的消息ID
            requestBody.put("reply_in_thread", true);
            requestBody.put("uuid", replyToMessageId);

            var response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getValidTenantAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            int code = responseNode.get("code").asInt(-1);

            if (code == 0) {
                log.debug("Reply message sent successfully to chat: {}, replyTo: {}", chatId, replyToMessageId);
                return true;
            } else {
                String msg = responseNode.get("msg").asText("unknown error");
                log.error("Failed to send reply message to Feishu, code: {}, msg: {}", code, msg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending reply message to Feishu chat: {}", chatId, e);
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

        try {
            String url = config.getApiBaseUrl() + "/open-apis/contact/v3/users/" + userId;

            var response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getValidTenantAccessToken())
                    .retrieve()
                    .toEntity(String.class);

            String body = response.getBody();
            userInfoCache.put(userId, body);
            return body;
        } catch (Exception e) {
            log.error("Error getting user info for userId: {}", userId, e);
            return null;
        }
    }

    @Override
    public String getChatInfo(String chatId) {
        if (!validateState()) {
            return null;
        }

        if (chatInfoCache.containsKey(chatId)) {
            return chatInfoCache.get(chatId);
        }

        try {
            String url = config.getApiBaseUrl() + "/open-apis/im/v1/chats/" + chatId;

            var response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getValidTenantAccessToken())
                    .retrieve()
                    .toEntity(String.class);

            String body = response.getBody();
            chatInfoCache.put(chatId, body);
            return body;
        } catch (Exception e) {
            log.error("Error getting chat info for chatId: {}", chatId, e);
            return null;
        }
    }

    /**
     * 处理事件回调
     *
     * @param payload 事件请求体
     * @return 处理结果
     */
    public String handleEventCallback(String payload) {
        if (!running.get()) {
            log.warn("FeishuAdapter is not running, ignoring event callback");
            return "{}";
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            // 处理URL验证挑战
            if (jsonNode.has("type") && "url_verification".equals(jsonNode.get("type").asText())) {
                String challenge = jsonNode.get("challenge").asText("");
                log.debug("Responding to URL verification challenge");
                return "{\"challenge\": \"" + challenge + "\"}";
            }

            // 构建PlatformMessage并交由handler处理
            PlatformMessage message = buildPlatformMessageFromEvent(jsonNode);

            if (messageHandler != null && message != null) {
                messageHandler.handleMessage(message);
            }

            return "{}";
        } catch (Exception e) {
            log.error("Error handling event callback", e);
            handleError(e);
            return "{}";
        }
    }

    /**
     * 刷新tenant_access_token
     */
    private void refreshTenantAccessToken() throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("app_id", config.getAppId());
        requestBody.put("app_secret", config.getAppSecret());

        var response = restClient.post()
                .uri(config.getTenantAccessTokenUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody.toString())
                .retrieve()
                .toEntity(String.class);

        JsonNode responseNode = objectMapper.readTree(response.getBody());
        int code = responseNode.get("code").asInt(-1);

        if (code == 0) {
            tenantAccessToken = responseNode.get("tenant_access_token").asText();
            int expire = responseNode.get("expire").asInt(7200);
            tokenExpireTime = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("Feishu tenant_access_token refreshed, expires in {} seconds", expire);
        } else {
            String msg = responseNode.get("msg").asText("unknown error");
            throw new RuntimeException("Failed to get Feishu tenant_access_token, code: " + code + ", msg: " + msg);
        }
    }

    /**
     * 获取有效的tenant_access_token
     */
    private String getValidTenantAccessToken() throws Exception {
        if (tenantAccessToken == null || System.currentTimeMillis() > tokenExpireTime) {
            refreshTenantAccessToken();
        }
        return tenantAccessToken;
    }

    /**
     * 验证适配器状态
     */
    private boolean validateState() {
        if (!running.get()) {
            log.warn("FeishuAdapter is not running");
            return false;
        }
        if (!config.isEnabled()) {
            log.warn("FeishuAdapter is disabled");
            return false;
        }
        return true;
    }

    /**
     * 从事件数据构建PlatformMessage
     */
    private PlatformMessage buildPlatformMessageFromEvent(JsonNode data) {
        try {
            JsonNode event = data.has("event") ? data.get("event") : null;
            if (event == null) {
                log.debug("No event data in callback");
                return null;
            }

            JsonNode message = event.has("message") ? event.get("message") : null;
            if (message == null) {
                log.debug("No message in event data");
                return null;
            }

            String messageId = message.has("message_id") ? message.get("message_id").asText() : "";
            String senderId = message.has("sender_id") ? message.get("sender_id").get("user_id").asText("") : "";
            String chatId = message.has("chat_id") ? message.get("chat_id").asText() : senderId;
            String content = message.has("content") ? parseMessageContent(message.get("content").asText("")) : "";
            String chatType = message.has("chat_type") ? message.get("chat_type").asText() : "private";

            boolean isGroup = "group".equals(chatType);

            return PlatformMessage.builder()
                    .messageId(messageId)
                    .platformType(PLATFORM_TYPE)
                    .platformUserId(senderId)
                    .platformChatId(chatId)
                    .content(content)
                    .messageType("text")
                    .isGroup(isGroup)
                    .rawData(objectMapper.convertValue(data, Map.class))
                    .build();
        } catch (Exception e) {
            log.error("Error building PlatformMessage from event data", e);
            return null;
        }
    }

    /**
     * 解析飞书消息内容JSON
     */
    private String parseMessageContent(String contentJson) {
        try {
            JsonNode contentNode = objectMapper.readTree(contentJson);
            if (contentNode.has("text")) {
                return contentNode.get("text").asText();
            }
            return contentJson;
        } catch (Exception e) {
            return contentJson;
        }
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
