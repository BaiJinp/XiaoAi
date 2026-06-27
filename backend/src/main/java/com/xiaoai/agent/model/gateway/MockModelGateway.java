package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class MockModelGateway implements ModelGatewayAdapter {

    private final ModelGatewaySupport modelGatewaySupport;

    public MockModelGateway(ModelGatewaySupport modelGatewaySupport) {
        this.modelGatewaySupport = modelGatewaySupport;
    }

    @Override
public String providerType() {
        return "mock";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        modelGatewaySupport.validateModel(model, "chat");
        long start = System.currentTimeMillis();
        Integer promptTokens = modelGatewaySupport.estimateTokens(command.getPrompt());
        String content = "Mock response: " + command.getPrompt();
        Integer completionTokens = modelGatewaySupport.estimateTokens(content);
        ModelCallLog log = modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                "chat", promptTokens, completionTokens, "success", null,
                command.getPrompt(), content, modelGatewaySupport.elapsedMs(start));
        return ChatModelResponse.builder()
                .content(content)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .modelCallLogId(log.getId())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        modelGatewaySupport.validateModel(model, "embedding");
        long start = System.currentTimeMillis();
        Integer promptTokens = modelGatewaySupport.estimateTokens(command.getInput());
        List<Double> embedding = List.of(0.1D, 0.2D, 0.3D);
        ModelCallLog log = modelGatewaySupport.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                "embedding", promptTokens, 0, "success", null,
                command.getInput(), embedding.toString(), modelGatewaySupport.elapsedMs(start));
        return EmbeddingModelResponse.builder()
                .embedding(embedding)
                .promptTokens(promptTokens)
                .totalTokens(promptTokens)
                .modelCallLogId(log.getId())
                .build();
    }
}
