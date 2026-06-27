package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.service.ModelConfigService;
import com.xiaoai.agent.model.service.ModelProviderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingModelGatewayTest {

    private final ModelConfigService modelConfigService = mock(ModelConfigService.class);
    private final ModelProviderService modelProviderService = mock(ModelProviderService.class);
    private final TestAdapter mockAdapter = new TestAdapter("mock");
    private final TestAdapter openAiAdapter = new TestAdapter("openai_compatible");
    private final RoutingModelGateway gateway = new RoutingModelGateway(
            modelConfigService,
            modelProviderService,
            List.of(mockAdapter, openAiAdapter));

    @Test
    void chatShouldRouteByProviderType() {
        ModelConfig model = model("chat", 2L);
        ModelProvider provider = provider("openai_compatible", "active");
        when(modelConfigService.getModelConfig(1L)).thenReturn(model);
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider);
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        ChatModelResponse response = gateway.chat(command);

        assertThat(response.getContent()).isEqualTo("openai_compatible:生成项目周报");
        assertThat(openAiAdapter.chatCalled).isTrue();
        assertThat(mockAdapter.chatCalled).isFalse();
        assertThat(openAiAdapter.context.getModel()).isSameAs(model);
        assertThat(openAiAdapter.context.getProvider()).isSameAs(provider);
    }

    @Test
    void embeddingShouldRouteByProviderType() {
        ModelConfig model = model("embedding", 2L);
        ModelProvider provider = provider("mock", "active");
        when(modelConfigService.getModelConfig(1L)).thenReturn(model);
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider);
        EmbeddingModelCommand command = new EmbeddingModelCommand();
        command.setModelId(1L);
        command.setInput("项目风险");

        EmbeddingModelResponse response = gateway.embedding(command);

        assertThat(response.getEmbedding()).containsExactly(0.1D);
        assertThat(mockAdapter.embeddingCalled).isTrue();
        assertThat(openAiAdapter.embeddingCalled).isFalse();
        assertThat(mockAdapter.context.getModel()).isSameAs(model);
        assertThat(mockAdapter.context.getProvider()).isSameAs(provider);
    }

    @Test
    void chatShouldRejectInactiveProvider() {
        when(modelConfigService.getModelConfig(1L)).thenReturn(model("chat", 2L));
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider("mock", "disabled"));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> gateway.chat(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model provider is not active");
    }

    @Test
    void chatShouldRejectUnsupportedProviderType() {
        when(modelConfigService.getModelConfig(1L)).thenReturn(model("chat", 2L));
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider("unknown", "active"));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> gateway.chat(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unsupported model provider type: unknown");
    }

    private ModelConfig model(String modelType, Long providerId) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setProviderId(providerId);
        model.setModelType(modelType);
        model.setStatus("active");
        return model;
    }

    private ModelProvider provider(String providerType, String status) {
        ModelProvider provider = new ModelProvider();
        provider.setId(2L);
        provider.setProviderType(providerType);
        provider.setStatus(status);
        return provider;
    }

    private static final class TestAdapter implements ModelGatewayAdapter {
        private final String providerType;
        private boolean chatCalled;
        private boolean embeddingCalled;
        private ModelGatewayContext context;

        private TestAdapter(String providerType) {
            this.providerType = providerType;
        }

        @Override
        public String providerType() {
            return providerType;
        }

        @Override
        public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
            chatCalled = true;
            this.context = context;
            return ChatModelResponse.builder()
                    .content(providerType + ":" + command.getPrompt())
                    .promptTokens(1)
                    .completionTokens(1)
                    .totalTokens(2)
                    .build();
        }

        @Override
        public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
            embeddingCalled = true;
            this.context = context;
            return EmbeddingModelResponse.builder()
                    .embedding(List.of(0.1D))
                    .promptTokens(1)
                    .totalTokens(1)
                    .build();
        }
    }
}
