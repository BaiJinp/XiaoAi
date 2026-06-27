package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelChunk;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import reactor.core.publisher.Flux;

public interface ModelGatewayAdapter {

    String providerType();

    ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context);

    /**
     * 流式聊天调用（token-by-token）
     * 默认实现：包装同步调用为单元素 Flux
     */
    default Flux<ChatModelChunk> chatStream(ChatModelCommand command, ModelGatewayContext context) {
        return Flux.defer(() -> {
            ChatModelResponse response = chat(command, context);
            return Flux.just(ChatModelChunk.builder()
                    .content(response.getContent())
                    .promptTokens(response.getPromptTokens())
                    .completionTokens(response.getCompletionTokens())
                    .totalTokens(response.getTotalTokens())
                    .done(true)
                    .build());
        });
    }

    EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context);
}
