package com.xiaoai.agent.model.gateway.openai;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.gateway.ModelGatewayAdapter;
import com.xiaoai.agent.model.gateway.ModelGatewayContext;
import com.xiaoai.agent.model.gateway.ModelGatewaySupport;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class OpenAiCompatibleModelGateway implements ModelGatewayAdapter {

    private final RestClient restClient;
    private final ModelGatewaySupport modelGatewaySupport;
    private final OpenAiCompatibleSupport openAiSupport;

    @Autowired
    public OpenAiCompatibleModelGateway(RestClient.Builder restClientBuilder,
                                        ModelGatewaySupport modelGatewaySupport,
                                        OpenAiCompatibleSupport openAiSupport) {
        this(restClientBuilder.build(), modelGatewaySupport, openAiSupport);
    }

    OpenAiCompatibleModelGateway(RestClient restClient,
                                 ModelGatewaySupport modelGatewaySupport,
                                 OpenAiCompatibleSupport openAiSupport) {
        this.restClient = restClient;
        this.modelGatewaySupport = modelGatewaySupport;
        this.openAiSupport = openAiSupport;
    }

    @Override
public String providerType() {
        return "openai_compatible";
    }

    @Override
public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        modelGatewaySupport.validateModel(model, "chat");
        long start = System.currentTimeMillis();
        String apiKey = null;
        boolean remoteAttempted = false;
        try {
            apiKey = openAiSupport.apiKey(context.getProvider().getAuthConfigJson());
            OpenAiCompatibleModelOptions options = openAiSupport.options(model.getConfigJson());
            OpenAiCompatibleChatRequest request = OpenAiCompatibleChatRequest.builder()
                    .model(model.getModelCode())
                    .messages(List.of(OpenAiCompatibleChatRequest.Message.builder()
                            .role("user")
                            .content(command.getPrompt())
                            .build()))
                    .temperature(options.getTemperature())
                    .maxTokens(options.getMaxTokens())
                    .topP(options.getTopP())
                    .build();
            remoteAttempted = true;
            OpenAiCompatibleChatResponse remoteResponse = restClient.post()
                    .uri(openAiSupport.endpoint(context.getProvider().getBaseUrl(), "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(OpenAiCompatibleChatResponse.class);
            String content = chatContent(remoteResponse);
            int promptTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getPromptTokens() == null
                    ? modelGatewaySupport.estimateTokens(command.getPrompt())
                    : remoteResponse.getUsage().getPromptTokens();
            int completionTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getCompletionTokens() == null
                    ? modelGatewaySupport.estimateTokens(content)
                    : remoteResponse.getUsage().getCompletionTokens();
            int totalTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getTotalTokens() == null
                    ? promptTokens + completionTokens
                    : remoteResponse.getUsage().getTotalTokens();
            ModelCallLog log = modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", promptTokens, completionTokens, totalTokens, "success", null,
                    command.getPrompt(), content, modelGatewaySupport.elapsedMs(start));
            return ChatModelResponse.builder()
                    .content(content)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .modelCallLogId(log.getId())
                    .build();
        } catch (BusinessException e) {
            if (!remoteAttempted) {
                throw e;
            }
            modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", modelGatewaySupport.estimateTokens(command.getPrompt()), 0,
                    "failed", sanitize(e.getMessage(), apiKey), command.getPrompt(), null, modelGatewaySupport.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible chat call failed");
        } catch (Exception e) {
            modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", modelGatewaySupport.estimateTokens(command.getPrompt()), 0,
                    "failed", sanitize(e.getMessage(), apiKey), command.getPrompt(), null, modelGatewaySupport.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible chat call failed");
        }
    }

    @Override
public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        modelGatewaySupport.validateModel(model, "embedding");
        long start = System.currentTimeMillis();
        String apiKey = null;
        boolean remoteAttempted = false;
        try {
            apiKey = openAiSupport.apiKey(context.getProvider().getAuthConfigJson());
            OpenAiCompatibleEmbeddingRequest request = OpenAiCompatibleEmbeddingRequest.builder()
                    .model(model.getModelCode())
                    .input(command.getInput())
                    .build();
            remoteAttempted = true;
            OpenAiCompatibleEmbeddingResponse remoteResponse = restClient.post()
                    .uri(openAiSupport.endpoint(context.getProvider().getBaseUrl(), "/embeddings"))
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(OpenAiCompatibleEmbeddingResponse.class);
            List<Double> embedding = embedding(remoteResponse);
            int promptTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getPromptTokens() == null
                    ? modelGatewaySupport.estimateTokens(command.getInput())
                    : remoteResponse.getUsage().getPromptTokens();
            int totalTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getTotalTokens() == null
                    ? promptTokens
                    : remoteResponse.getUsage().getTotalTokens();
            ModelCallLog log = modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "embedding", promptTokens, 0, totalTokens, "success", null,
                    command.getInput(), embedding.toString(), modelGatewaySupport.elapsedMs(start));
            return EmbeddingModelResponse.builder()
                    .embedding(embedding)
                    .promptTokens(promptTokens)
                    .totalTokens(totalTokens)
                    .modelCallLogId(log.getId())
                    .build();
        } catch (BusinessException e) {
            if (!remoteAttempted) {
                throw e;
            }
            modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "embedding", modelGatewaySupport.estimateTokens(command.getInput()), 0,
                    "failed", sanitize(e.getMessage(), apiKey), command.getInput(), null, modelGatewaySupport.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible embedding call failed");
        } catch (Exception e) {
            modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "embedding", modelGatewaySupport.estimateTokens(command.getInput()), 0,
                    "failed", sanitize(e.getMessage(), apiKey), command.getInput(), null, modelGatewaySupport.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible embedding call failed");
        }
    }

    private String chatContent(OpenAiCompatibleChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().getContent() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible chat response is empty");
        }
        return response.getChoices().get(0).getMessage().getContent();
    }

    private List<Double> embedding(OpenAiCompatibleEmbeddingResponse response) {
        if (response == null || response.getData() == null || response.getData().isEmpty()
                || response.getData().get(0).getEmbedding() == null
                || response.getData().get(0).getEmbedding().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible embedding response is empty");
        }
        return response.getData().get(0).getEmbedding();
    }

    private String sanitize(String message, String apiKey) {
        if (message == null) {
            return null;
        }
        String sanitized = apiKey == null ? message : message.replace(apiKey, "******");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
