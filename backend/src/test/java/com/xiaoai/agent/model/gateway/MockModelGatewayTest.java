package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockModelGatewayTest {

    private final ModelCallLogService modelCallLogService = mock(ModelCallLogService.class);
    private final ModelGatewaySupport modelGatewaySupport = new ModelGatewaySupport(modelCallLogService);
    private final MockModelGateway modelGateway = new MockModelGateway(modelGatewaySupport);

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-model")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void providerTypeShouldBeMock() {
        assertThat(modelGateway.providerType()).isEqualTo("mock");
    }

    @Test
    void chatShouldReturnMockContentAndSaveCallLog() {
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(77L);
            return true;
        });
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setTaskId(10L);
        command.setRunId(11L);
        command.setPrompt("生成项目周报");

        ChatModelResponse response = modelGateway.chat(command, context("chat"));

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getTenantId()).isEqualTo(100L);
        assertThat(log.getCallType()).isEqualTo("chat");
        assertThat(log.getStatus()).isEqualTo("success");
        assertThat(log.getTotalTokens()).isGreaterThan(0);
        assertThat(log.getTraceId()).isEqualTo("trace-model");
        assertThat(response.getContent()).startsWith("Mock response:");
        assertThat(response.getModelCallLogId()).isEqualTo(77L);
    }

    @Test
    void embeddingShouldReturnMockVectorAndSaveCallLog() {
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(78L);
            return true;
        });
        EmbeddingModelCommand command = new EmbeddingModelCommand();
        command.setModelId(1L);
        command.setInput("项目风险");

        EmbeddingModelResponse response = modelGateway.embedding(command, context("embedding"));

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getCallType()).isEqualTo("embedding");
        assertThat(log.getCompletionTokens()).isZero();
        assertThat(response.getEmbedding()).containsExactly(0.1D, 0.2D, 0.3D);
        assertThat(response.getModelCallLogId()).isEqualTo(78L);
    }

    @Test
    void chatShouldRejectEmbeddingModelConfig() {
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> modelGateway.chat(command, context("embedding")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model type mismatch");
    }

    @Test
    void embeddingShouldRejectChatModelConfig() {
        EmbeddingModelCommand command = new EmbeddingModelCommand();
        command.setModelId(1L);
        command.setInput("项目风险");

        assertThatThrownBy(() -> modelGateway.embedding(command, context("chat")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model type mismatch");
    }

    @Test
    void chatShouldRejectInactiveModelConfig() {
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> modelGateway.chat(command, context(model("chat", "disabled"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model config is not active");
    }

    @Test
    void chatShouldEstimateBlankPromptAsZeroTokens() {
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(79L);
            return true;
        });
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt(" ");

        ChatModelResponse response = modelGateway.chat(command, context("chat"));

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getPromptTokens()).isZero();
        assertThat(response.getPromptTokens()).isZero();
        assertThat(response.getModelCallLogId()).isEqualTo(79L);
    }

    @Test
    void chatShouldTruncateRequestAndResponseSummaryInCallLog() {
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(80L);
            return true;
        });
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("A".repeat(1200));

        modelGateway.chat(command, context("chat"));

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getRequestSummary()).hasSize(1000);
        assertThat(log.getResponseSummary()).hasSize(1000);
    }

    private ModelGatewayContext context(String modelType) {
        return context(model(modelType, "active"));
    }

    private ModelGatewayContext context(ModelConfig model) {
        ModelProvider provider = new ModelProvider();
        provider.setId(2L);
        provider.setProviderType("mock");
        provider.setStatus("active");
        return ModelGatewayContext.builder()
                .model(model)
                .provider(provider)
                .build();
    }

    private ModelConfig model(String modelType, String status) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setTenantId(100L);
        model.setProviderId(2L);
        model.setModelType(modelType);
        model.setStatus(status);
        return model;
    }
}
