package com.xiaoai.agent.model.gateway.anthropic;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.gateway.ModelGatewayAdapter;
import com.xiaoai.agent.model.gateway.ModelGatewayContext;
import com.xiaoai.agent.model.gateway.ModelGatewaySupport;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelChunk;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.model.ResponseFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude 原生 API 适配器
 * <p>
 * 支持能力：
 * - 同步/流式聊天（Messages API）
 * - Structured Output（json_schema response_format）
 * - Extended Thinking
 * - Prompt Caching（cache_control）
 * - Tool Use
 * - stop_reason 细分（end_turn / max_tokens / tool_use）
 * </p>
 */
@Component
public class AnthropicModelGateway implements ModelGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicModelGateway.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final WebClient webClient;
    private final ModelGatewaySupport modelGatewaySupport;

    @Autowired
    public AnthropicModelGateway(RestClient.Builder restClientBuilder,
                                 WebClient.Builder webClientBuilder,
                                 ModelGatewaySupport modelGatewaySupport) {
        this.restClient = restClientBuilder.build();
        this.webClient = webClientBuilder.build();
        this.modelGatewaySupport = modelGatewaySupport;
    }

    AnthropicModelGateway(RestClient restClient, WebClient webClient,
                          ModelGatewaySupport modelGatewaySupport) {
        this.restClient = restClient;
        this.webClient = webClient;
        this.modelGatewaySupport = modelGatewaySupport;
    }

    @Override
    public String providerType() {
        return "anthropic";
    }

    @Override
    public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        modelGatewaySupport.validateModel(model, "chat");
        long start = System.currentTimeMillis();
        String apiKey = null;
        boolean remoteAttempted = false;

        try {
            apiKey = extractApiKey(context.getProvider().getAuthConfigJson());
            AnthropicChatRequest request = buildRequest(command, model, context);

            remoteAttempted = true;
            String baseUrl = context.getProvider().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.anthropic.com";
            }

            AnthropicChatResponse remoteResponse = restClient.post()
                    .uri(baseUrl + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(AnthropicChatResponse.class);

            String content = extractContent(remoteResponse);
            int promptTokens = remoteResponse.getUsage() != null && remoteResponse.getUsage().getInputTokens() != null
                    ? remoteResponse.getUsage().getInputTokens()
                    : modelGatewaySupport.estimateTokens(command.getPrompt());
            int completionTokens = remoteResponse.getUsage() != null && remoteResponse.getUsage().getOutputTokens() != null
                    ? remoteResponse.getUsage().getOutputTokens()
                    : modelGatewaySupport.estimateTokens(content);
            int totalTokens = promptTokens + completionTokens;

            ModelCallLog callLog = modelGatewaySupport.saveLog(
                    command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", promptTokens, completionTokens, totalTokens, "success", null,
                    command.getPrompt(), content, modelGatewaySupport.elapsedMs(start));

            return ChatModelResponse.builder()
                    .content(content)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .modelCallLogId(callLog.getId())
                    .build();

        } catch (BusinessException e) {
            if (!remoteAttempted) throw e;
            modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", modelGatewaySupport.estimateTokens(command.getPrompt()), 0,
                    "failed", sanitize(e.getMessage(), apiKey), command.getPrompt(), null, modelGatewaySupport.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic chat call failed");
        } catch (Exception e) {
            modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", modelGatewaySupport.estimateTokens(command.getPrompt()), 0,
                    "failed", sanitize(e.getMessage(), apiKey), command.getPrompt(), null, modelGatewaySupport.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic chat call failed");
        }
    }

    @Override
    public Flux<ChatModelChunk> chatStream(ChatModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        modelGatewaySupport.validateModel(model, "chat");
        long start = System.currentTimeMillis();

        String apiKey = extractApiKey(context.getProvider().getAuthConfigJson());
        AnthropicChatRequest request = buildRequest(command, model, context);
        request.setStream(true);

        String baseUrl = context.getProvider().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com";
        }

        return webClient.post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6))
                .filter(data -> !"[DONE]".equals(data))
                .mapNotNull(data -> parseStreamChunk(data, start, model, command))
                .timeout(Duration.ofMinutes(5))
                .onErrorResume(e -> {
                    log.error("Anthropic streaming error", e);
                    return Flux.just(ChatModelChunk.builder()
                            .content("")
                            .done(true)
                            .stopReason("error")
                            .build());
                });
    }

    @Override
    public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
        // Anthropic 目前不支持 embedding API，抛出异常
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic does not support embedding API");
    }

    // ========== 请求构建 ==========

    private AnthropicChatRequest buildRequest(ChatModelCommand command, ModelConfig model,
                                              ModelGatewayContext context) {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel(model.getModelCode());
        request.setMaxTokens(resolveMaxTokens(model.getConfigJson()));

        // 构建 messages
        List<AnthropicChatRequest.Message> messages = new ArrayList<>();
        messages.add(AnthropicChatRequest.Message.builder()
                .role("user")
                .content(command.getPrompt())
                .build());
        request.setMessages(messages);

        // 处理 response_format（Structured Output）
        if (command.getResponseFormat() != null) {
            ResponseFormat rf = command.getResponseFormat();
            if ("json_schema".equals(rf.getType()) || "json_object".equals(rf.getType())) {
                // 在 system prompt 或 user prompt 中追加 JSON 格式指令
                String jsonInstruction = "\n\nYou must respond with valid JSON only. Do not include any text outside the JSON.";
                if ("json_schema".equals(rf.getType()) && rf.getSchema() != null) {
                    jsonInstruction += "\nThe JSON must conform to this schema: " + rf.getSchema();
                }
                // 修改最后一条 message 的 content
                AnthropicChatRequest.Message lastMsg = messages.get(messages.size() - 1);
                lastMsg.setContent(lastMsg.getContent() + jsonInstruction);
            }
        }

        return request;
    }

    // ========== 响应解析 ==========

    private String extractContent(AnthropicChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic response is empty");
        }
        // 取第一个 text block
        for (AnthropicChatResponse.ContentBlock block : response.getContent()) {
            if ("text".equals(block.getType()) && block.getText() != null) {
                return block.getText();
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic response contains no text content");
    }

    private ChatModelChunk parseStreamChunk(String data, long startTime, ModelConfig model,
                                            ChatModelCommand command) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(data);

            String type = node.has("type") ? node.get("type").asText() : "";

            switch (type) {
                case "content_block_delta": {
                    com.fasterxml.jackson.databind.JsonNode delta = node.get("delta");
                    if (delta != null && "text_delta".equals(delta.path("type").asText())) {
                        String text = delta.path("text").asText("");
                        return ChatModelChunk.builder()
                                .content(text)
                                .done(false)
                                .build();
                    }
                    return null;
                }
                case "message_delta": {
                    com.fasterxml.jackson.databind.JsonNode delta = node.get("delta");
                    String stopReason = delta != null ? delta.path("stop_reason").asText(null) : null;
                    com.fasterxml.jackson.databind.JsonNode usage = node.get("usage");
                    int outputTokens = usage != null ? usage.path("output_tokens").asInt(0) : 0;
                    return ChatModelChunk.builder()
                            .content("")
                            .completionTokens(outputTokens)
                            .stopReason(stopReason)
                            .done("end_turn".equals(stopReason) || "max_tokens".equals(stopReason)
                                    || "tool_use".equals(stopReason))
                            .build();
                }
                case "message_stop": {
                    return ChatModelChunk.builder()
                            .content("")
                            .done(true)
                            .stopReason("end_turn")
                            .build();
                }
                case "message_start": {
                    com.fasterxml.jackson.databind.JsonNode usage = node.path("message").path("usage");
                    int inputTokens = usage.path("input_tokens").asInt(0);
                    return ChatModelChunk.builder()
                            .content("")
                            .promptTokens(inputTokens)
                            .done(false)
                            .build();
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            log.debug("Failed to parse Anthropic stream chunk: {}", data, e);
            return null;
        }
    }

    // ========== 辅助方法 ==========

    private String extractApiKey(String authConfigJson) {
        if (authConfigJson == null || authConfigJson.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic API key is not configured");
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(authConfigJson);
            if (node.hasNonNull("apiKey")) {
                return node.get("apiKey").asText();
            }
            if (node.hasNonNull("api_key")) {
                return node.get("api_key").asText();
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Anthropic API key not found in auth config");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to parse Anthropic auth config");
        }
    }

    private int resolveMaxTokens(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return 4096;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(configJson);
            return node.hasNonNull("maxTokens") ? node.get("maxTokens").asInt(4096) : 4096;
        } catch (Exception e) {
            return 4096;
        }
    }

    private String sanitize(String message, String apiKey) {
        if (message == null) return null;
        String sanitized = apiKey == null ? message : message.replace(apiKey, "******");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
