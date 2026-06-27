package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelGatewayControllerTest {

    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final ModelGatewayController controller = new ModelGatewayController(modelGateway);

    @Test
    void chatShouldDelegateToModelGateway() {
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");
        when(modelGateway.chat(command)).thenReturn(ChatModelResponse.builder()
                .content("ok")
                .promptTokens(1)
                .completionTokens(1)
                .totalTokens(2)
                .build());

        assertThat(controller.chat(command).getData().getContent()).isEqualTo("ok");
    }

    @Test
    void embeddingShouldDelegateToModelGateway() {
        EmbeddingModelCommand command = new EmbeddingModelCommand();
        command.setModelId(2L);
        command.setInput("项目风险");
        when(modelGateway.embedding(command)).thenReturn(EmbeddingModelResponse.builder()
                .embedding(List.of(0.1D, 0.2D))
                .promptTokens(1)
                .totalTokens(1)
                .build());

        assertThat(controller.embedding(command).getData().getEmbedding()).containsExactly(0.1D, 0.2D);
    }
}
