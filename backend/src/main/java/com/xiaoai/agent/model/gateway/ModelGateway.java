package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelChunk;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import reactor.core.publisher.Flux;

public interface ModelGateway {

    /**
     * 同步聊天调用
     */
    ChatModelResponse chat(ChatModelCommand command);

    /**
     * 流式聊天调用（token-by-token）
     */
    Flux<ChatModelChunk> chatStream(ChatModelCommand command);

    /**
     * Embedding 调用
     */
    EmbeddingModelResponse embedding(EmbeddingModelCommand command);
}
