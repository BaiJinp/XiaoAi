package com.xiaoai.agent.platform.adapter.dingtalk;

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
import java.net.URLEncoder;

/**
 * 钉钉平台适配器
 * <p>
 * 支持钉钉机器人消息收发、Webhook回调接收
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Slf4j
@Component
public class DingtalkAdapter implements PlatformAdapter {

    public static final String PLATFORM_TYPE = "dingtalk";

    private final DingtalkConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlatformMessageHandler messageHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String accessToken;
    private long tokenExpireTime;

    /**
     * 缓存的用户信息
     */
    private final Map<String, String> userInfoCache = new ConcurrentHashMap<>();

    /**
     * 缓存的群组信息
     */
    private final Map<String, String> chatInfoCache = new ConcurrentHashMap<>();

    public DingtalkAdapter(DingtalkConfig config) {
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
            log.warn("DingtalkAdapter is already running");
            return;
        }

        if (!config.isEnabled()) {
            log.warn("DingtalkAdapter is disabled in configuration");
            return;
        }

        try {
            refreshAccessToken();
            running.set(true);
            log.info("DingtalkAdapter started successfully, appKey: {}", config.getAppKey());

            if (messageHandler != null) {
                messageHandler.handleStatusChange(PLATFORM_TYPE, true);
            }
        } catch (Exception e) {
            log.error("Failed to start DingtalkAdapter", e);
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

        log.info("DingtalkAdapter stopped");

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
            String url = buildSignedWebhookUrl();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("msgtype", "text");
            requestBody.put("at", objectMapper.createObjectNode());

            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("content", message);
            requestBody.set("text", textNode);

            // 如果是群聊，设置@的人
            if (chatId != null && !chatId.isEmpty()) {
                requestBody.get("at").put("atUserIds", new String[]{chatId});
            }

            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .toEntity(String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            int errcode = responseNode.get("errcode").asInt(1);

            if (errcode == 0) {
                log.debug("Message sent successfully to chat: {}", chatId);
                return true;
            } else {
                String errmsg = responseNode.get("errmsg").asText("unknown error");
                log.error("Failed to send message to DingTalk, errcode: {}, errmsg: {}", errcode, errmsg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending message to DingTalk chat: {}", chatId, e);
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
            String url = buildSignedWebhookUrl();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("msgtype", "markdown");
            requestBody.put("at", objectMapper.createObjectNode());

            ObjectNode markdownNode = objectMapper.createObjectNode();
            markdownNode.put("title", "Message");
            markdownNode.put("text", message);
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
                log.debug("Markdown message sent successfully to chat: {}", chatId);
                return true;
            } else {
                String errmsg = responseNode.get("errmsg").asText("unknown error");
                log.error("Failed to send markdown message to DingTalk, errcode: {}, errmsg: {}", errcode, errmsg);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending markdown message to DingTalk chat: {}", chatId, e);
            handleError(e);
            return false;
        }
    }

    @Override
    public boolean replyMessage(String chatId, String replyToMessageId, String message) {
        // 钉钉机器人不支持直接回复特定消息，退化为普通发送
        log.debug("DingTalk robot does not support direct reply, falling back to normal send");
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

        if (userInfoCache.containsKey(userId)) {
            return userInfoCache.get(userId);
        }

        try {
            String url = config.getApiBaseUrl() + "/topapi/v2/user/get?access_token=" + getValidAccessToken();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("userid", userId);

            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
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
            String url = config.getApiBaseUrl() + "/topapi/v2/chat/get?access_token=" + getValidAccessToken();

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("open_conversation_id", chatId);

            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
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
            log.warn("DingtalkAdapter is not running, ignoring webhook callback");
            return "{\"errcode\": 1, \"errmsg\": \"adapter not running\"}";
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

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
            int expiresIn = responseNode.get("expires_in").asInt(7200);
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
            log.info("DingTalk access_token refreshed, expires in {} seconds", expiresIn);
        } else {
            String errmsg = responseNode.get("errmsg").asText("unknown error");
            throw new RuntimeException("Failed to get DingTalk access_token, errcode: " + errcode + ", errmsg: " + errmsg);
        }
    }

    /**
     * 获取有效的access_token
     */
    private String getValidAccessToken() throws Exception {
        if (accessToken == null || System.currentTimeMillis() > tokenExpireTime) {
            refreshAccessToken();
        }
        return accessToken;
    }

    /**
     * 构建带签名的Webhook URL
     */
    private String buildSignedWebhookUrl() throws Exception {
        String baseUrl = config.getSendMessageUrlTemplate() + config.getRobotCode();

        if (config.getWebhookSecret() != null && !config.getWebhookSecret().isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String sign = generateSign(timestamp, config.getWebhookSecret());
            return baseUrl + "&timestamp=" + timestamp + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8.name());
        }

        return baseUrl;
    }

    /**
     * 生成签名
     */
    private String generateSign(long timestamp, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    /**
     * 验证适配器状态
     */
    private boolean validateState() {
        if (!running.get()) {
            log.warn("DingtalkAdapter is not running");
            return false;
        }
        if (!config.isEnabled()) {
            log.warn("DingtalkAdapter is disabled");
            return false;
        }
        return true;
    }

    /**
     * 从Webhook数据构建PlatformMessage
     */
    private PlatformMessage buildPlatformMessageFromWebhook(JsonNode data) {
        try {
            String senderId = data.has("senderId") ? data.get("senderId").asText() : "";
            String content = data.has("text") ? data.get("text").get("content").asText() : "";
            String msgId = data.has("msgId") ? data.get("msgId").asText() : "";
            String conversationId = data.has("conversationId") ? data.get("conversationId").asText() : senderId;
            boolean isGroup = data.has("conversationType") && "2".equals(data.get("conversationType").asText());

            return PlatformMessage.builder()
                    .messageId(msgId)
                    .platformType(PLATFORM_TYPE)
                    .platformUserId(senderId)
                    .platformChatId(conversationId)
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
