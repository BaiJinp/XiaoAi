package com.xiaoai.agent.platform.adapter.wecom;

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
 * 企业微信平台适配器
 * <p>
 * 支持企业微信应用消息收发、Webhook回调接收
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Slf4j
@Component
public class WecomAdapter implements PlatformAdapter {

    public static final String PLATFORM_TYPE = "wecom";

    private final WecomConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlatformMessageHandler messageHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String accessToken;
    private long tokenExpireTime;

    /**
     * 缓存的用户信息，key: userId, value: userInfo JSON
     */
    private final Map<String, String> userInfoCache = new ConcurrentHashMap<>();

    /**
     * 缓存的群组信息，key: chatId, value: chatInfo JSON
     */
    private final Map<String, String> chatInfoCache = new ConcurrentHashMap<>();

    public WecomAdapter(WecomConfig config) {
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
            log.warn("WecomAdapter is already running");
            return;
        }

        if (!config.isEnabled()) {
            log.warn("WecomAdapter is disabled in configuration");
            return;
        }

        try {
            // 获取access_token
            refreshAccessToken();
            running.set(true);
            log.info("WecomAdapter started successfully, corpid: {}", config.getCorpid());

            if (messageHandler != null) {
                messageHandler.handleStatusChange(PLATFORM_TYPE, true);
            }
        } catch (Exception e) {
            log.error("Failed to start WecomAdapter", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!running.get()) {
            return;
        }

        running.set(false);
        accessToken = null;
        tokenExpireTime = 0;
        userInfoCache.clear();
        chatInfoCache.clear();

        log.info("WecomAdapter stopped");

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
            String url = config.getSendMessageUrl() + getValidAccessToken();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("agentid", config.getAgentId());
            requestBody.put("touser", chatId);
            requestBody.put("msgtype", "text");

            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("content", message);
            requestBody.set("text", textNode);

            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            int errcode = responseNode.get("errcode").asInt(1);

            if (errcode == 0) {
                log.debug("Message sent successfully to user: {}", chatId);
                return true;
            } else {
                String errmsg = responseNode.get("errmsg").asText("unknown error");
                log.error("Failed to send message to WeCom, errcode: {}, errmsg: {}", errcode, errmsg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending message to WeCom user: {}", chatId, e);
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
            String url = config.getSendMessageUrl() + getValidAccessToken();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("agentid", config.getAgentId());
            requestBody.put("touser", chatId);
            requestBody.put("msgtype", "markdown");

            ObjectNode markdownNode = objectMapper.createObjectNode();
            markdownNode.put("content", message);
            requestBody.set("markdown", markdownNode);

            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            int errcode = responseNode.get("errcode").asInt(1);

            if (errcode == 0) {
                log.debug("Markdown message sent successfully to user: {}", chatId);
                return true;
            } else {
                String errmsg = responseNode.get("errmsg").asText("unknown error");
                log.error("Failed to send markdown message to WeCom, errcode: {}, errmsg: {}", errcode, errmsg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending markdown message to WeCom user: {}", chatId, e);
            handleError(e);
            return false;
        }
    }

    @Override
    public boolean replyMessage(String chatId, String replyToMessageId, String message) {
        // 企业微信不支持直接回复特定消息，退化为普通发送
        log.debug("WeCom does not support direct reply, falling back to normal send");
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
        if (!validateState()) {
            return null;
        }

        // 先从缓存获取
        if (userInfoCache.containsKey(userId)) {
            return userInfoCache.get(userId);
        }

        try {
            String url = config.getApiBaseUrl() + "/cgi-bin/user/get?access_token=" + getValidAccessToken()
                    + "&userid=" + userId;

            var response = restClient.get()
                    .uri(url)
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

        // 先从缓存获取
        if (chatInfoCache.containsKey(chatId)) {
            return chatInfoCache.get(chatId);
        }

        try {
            // 企业微信使用 department 或 chat 接口获取群组信息
            String url = config.getApiBaseUrl() + "/cgi-bin/chat/get?access_token=" + getValidAccessToken()
                    + "&chatid=" + chatId;

            var response = restClient.get()
                    .uri(url)
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
     * 处理Webhook回调消息
     *
     * @param payload Webhook请求体
     * @return 处理结果
     */
    public String handleWebhookCallback(String payload) {
        if (!running.get()) {
            log.warn("WecomAdapter is not running, ignoring webhook callback");
            return "{\"errcode\": 1, \"errmsg\": \"adapter not running\"}";
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            // 构建PlatformMessage
            PlatformMessage message = buildPlatformMessageFromWebhook(jsonNode);

            if (messageHandler != null && message != null) {
                messageHandler.handleMessage(message);
            }

            return "{\"errcode\": 0, \"errmsg\": \"ok\"}";
        } catch (Exception e) {
            log.error("Error handling webhook callback", e);
            handleError(e);
            return "{\"errcode\": 1, \"errmsg\": \"internal error\"}";
        }
    }

    /**
     * 刷新access_token
     */
    private void refreshAccessToken() throws Exception {
        var response = restClient.get()
                .uri(config.getAccessTokenUrl())
                .retrieve()
                .toEntity(String.class);

        JsonNode responseNode = objectMapper.readTree(response.getBody());
        int errcode = responseNode.get("errcode").asInt(1);

        if (errcode == 0) {
            accessToken = responseNode.get("access_token").asText();
            // token有效期通常为2小时，提前5分钟刷新
            int expiresIn = responseNode.get("expires_in").asInt(7200);
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
            log.info("WeCom access_token refreshed, expires in {} seconds", expiresIn);
        } else {
            String errmsg = responseNode.get("errmsg").asText("unknown error");
            throw new RuntimeException("Failed to get WeCom access_token, errcode: " + errcode + ", errmsg: " + errmsg);
        }
    }

    /**
     * 获取有效的access_token（如果过期则刷新）
     */
    private String getValidAccessToken() throws Exception {
        if (accessToken == null || System.currentTimeMillis() > tokenExpireTime) {
            refreshAccessToken();
        }
        return accessToken;
    }

    /**
     * 验证适配器状态
     */
    private boolean validateState() {
        if (!running.get()) {
            log.warn("WecomAdapter is not running");
            return false;
        }
        if (!config.isEnabled()) {
            log.warn("WecomAdapter is disabled");
            return false;
        }
        return true;
    }

    /**
     * 从Webhook数据构建PlatformMessage
     */
    private PlatformMessage buildPlatformMessageFromWebhook(JsonNode data) {
        try {
            String fromUserId = data.has("FromUserName") ? data.get("FromUserName").asText() : "";
            String content = data.has("Content") ? data.get("Content").asText() : "";
            String msgId = data.has("MsgId") ? data.get("MsgId").asText() : "";
            String chatId = fromUserId; // 私聊场景下chatId即为userId

            boolean isGroup = data.has("ChatType") && "group".equals(data.get("ChatType").asText());
            if (isGroup) {
                chatId = data.has("ChatId") ? data.get("ChatId").asText() : fromUserId;
            }

            return PlatformMessage.builder()
                    .messageId(msgId)
                    .platformType(PLATFORM_TYPE)
                    .platformUserId(fromUserId)
                    .platformChatId(chatId)
                    .content(content)
                    .messageType("text")
                    .isGroup(isGroup)
                    .rawData(objectMapper.convertValue(data, Map.class))
                    .build();
        } catch (Exception e) {
            log.error("Error building PlatformMessage from webhook data", e);
            return null;
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
