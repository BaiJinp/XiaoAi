package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.gateway.ModelGatewayContext;
import com.xiaoai.agent.model.gateway.ModelGatewaySupport;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleModelGatewayTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ModelCallLogService modelCallLogService = mock(ModelCallLogService.class);
    private final ModelGatewaySupport gatewaySupport = new ModelGatewaySupport(modelCallLogService);
    private final OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway(
            restClientBuilder.build(),
            gatewaySupport,
            new OpenAiCompatibleSupport(new ObjectMapper()));

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-openai")
                .build());
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(88L);
            return true;
        });
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        server.verify();
    }

    @Test
    void providerTypeShouldBeOpenAiCompatible() {
        assertThat(gateway.providerType()).isEqualTo("openai_compatible");
    }

    @Test
    void chatShouldCallOpenAiCompatibleEndpointAndSaveSuccessLog() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("生成项目周报"))
                .andExpect(jsonPath("$.temperature").value(0.2D))
                .andRespond(withSuccess("""
                        {
                          "choices": [{"message": {"content": "周报内容"}}],
                          "usage": {"prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 15}
                        }
                        """, MediaType.APPLICATION_JSON));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setTaskId(10L);
        command.setRunId(11L);
        command.setStepId(12L);
        command.setPrompt("生成项目周报");

        ChatModelResponse response = gateway.chat(command, context("https://api.example.com", "{\"temperature\":0.2}"));

        assertThat(response.getContent()).isEqualTo("周报内容");
        assertThat(response.getPromptTokens()).isEqualTo(5);
        assertThat(response.getCompletionTokens()).isEqualTo(7);
        assertThat(response.getTotalTokens()).isEqualTo(15);
        assertThat(response.getModelCallLogId()).isEqualTo(88L);
        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo("success");
        assertThat(log.getTotalTokens()).isEqualTo(15);
        assertThat(log.getRequestSummary()).isEqualTo("生成项目周报");
        assertThat(log.getResponseSummary()).isEqualTo("周报内容");
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void chatShouldSaveFailureLogWithoutApiKeyWhenProviderFails() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-secret"))
                .andRespond(withServerError());
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> gateway.chat(command, context("https://api.example.com", "{}", "sk-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OpenAI-compatible chat call failed");

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo("failed");
        assertThat(log.getErrorMessage()).doesNotContain("sk-secret");
    }

    @Test
    void embeddingShouldCallOpenAiCompatibleEndpointAndSaveSuccessLog() {
        server.expect(requestTo("https://api.example.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("embedding-test"))
                .andExpect(jsonPath("$.input").value("项目风险"))
                .andRespond(withSuccess("""
                        {
                          "data": [{"embedding": [0.1, 0.2, 0.3]}],
                          "usage": {"prompt_tokens": 4, "total_tokens": 6}
                        }
                        """, MediaType.APPLICATION_JSON));
        EmbeddingModelCommand command = new EmbeddingModelCommand();
        command.setModelId(1L);
        command.setTaskId(10L);
        command.setRunId(11L);
        command.setStepId(12L);
        command.setInput("项目风险");

        EmbeddingModelResponse response = gateway.embedding(command, embeddingContext("https://api.example.com"));

        assertThat(response.getEmbedding()).containsExactly(0.1D, 0.2D, 0.3D);
        assertThat(response.getPromptTokens()).isEqualTo(4);
        assertThat(response.getTotalTokens()).isEqualTo(6);
        assertThat(response.getModelCallLogId()).isEqualTo(88L);
        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("success");
        assertThat(captor.getValue().getCallType()).isEqualTo("embedding");
        assertThat(captor.getValue().getTotalTokens()).isEqualTo(6);
    }

    @Test
    void chatShouldEstimateTokensWhenUsageIsMissing() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"content": "12345"}}]}
                        """, MediaType.APPLICATION_JSON));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("12345");

        ChatModelResponse response = gateway.chat(command, context("https://api.example.com", "{}"));

        assertThat(response.getPromptTokens()).isEqualTo(2);
        assertThat(response.getCompletionTokens()).isEqualTo(2);
        assertThat(response.getTotalTokens()).isEqualTo(4);
        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        assertThat(captor.getValue().getTotalTokens()).isEqualTo(4);
    }

    @Test
    void chatShouldSaveFailureLogWhenSuccessfulResponseIsEmpty() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> gateway.chat(command, context("https://api.example.com", "{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OpenAI-compatible chat call failed");

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("failed");
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("OpenAI-compatible chat response is empty");
    }

    private ModelGatewayContext context(String baseUrl, String configJson) {
        return context(baseUrl, configJson, "sk-test");
    }

    private ModelGatewayContext context(String baseUrl, String configJson, String apiKey) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setProviderId(2L);
        model.setModelCode("gpt-test");
        model.setModelType("chat");
        model.setConfigJson(configJson);
        model.setStatus("active");
        ModelProvider provider = new ModelProvider();
        provider.setId(2L);
        provider.setProviderType("openai_compatible");
        provider.setBaseUrl(baseUrl);
        provider.setAuthConfigJson("{\"apiKey\":\"" + apiKey + "\"}");
        provider.setStatus("active");
        return ModelGatewayContext.builder()
                .model(model)
                .provider(provider)
                .build();
    }

    private ModelGatewayContext embeddingContext(String baseUrl) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setProviderId(2L);
        model.setModelCode("embedding-test");
        model.setModelType("embedding");
        model.setConfigJson("{}");
        model.setStatus("active");
        ModelProvider provider = new ModelProvider();
        provider.setId(2L);
        provider.setProviderType("openai_compatible");
        provider.setBaseUrl(baseUrl);
        provider.setAuthConfigJson("{\"apiKey\":\"sk-test\"}");
        provider.setStatus("active");
        return ModelGatewayContext.builder()
                .model(model)
                .provider(provider)
                .build();
    }
}
