package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.model.CreateModelProviderCommand;
import com.xiaoai.agent.model.model.ModelProviderResponse;
import com.xiaoai.agent.model.service.ModelProviderService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProviderControllerTest {

    private final ModelProviderService service = mock(ModelProviderService.class);
    private final ModelProviderController controller = new ModelProviderController(service);

    @Test
    void createShouldReturnProviderWithoutAuthConfigJson() {
        CreateModelProviderCommand command = new CreateModelProviderCommand();
        command.setProviderCode("openai-prod");
        command.setProviderName("OpenAI Production");
        command.setProviderType("openai_compatible");
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey("sk-live-secret");
        ModelProvider provider = provider();
        when(service.createModelProvider(command)).thenReturn(provider);

        ApiResponse<ModelProviderResponse> result = controller.create(command);

        assertThat(result.getCode()).isEqualTo("0");
        assertThat(result.getData().getId()).isEqualTo(10L);
        assertThat(result.getData().getProviderCode()).isEqualTo("openai-prod");
        assertThat(Arrays.stream(ModelProviderResponse.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("authConfigJson");
        verify(service).createModelProvider(command);
    }

    @Test
    void getByIdShouldReturnProviderWithoutAuthConfigJson() {
        when(service.getModelProvider(10L)).thenReturn(provider());

        ApiResponse<ModelProviderResponse> result = controller.getById(10L);

        assertThat(result.getData().getId()).isEqualTo(10L);
        assertThat(result.getData().getProviderType()).isEqualTo("openai_compatible");
        assertThat(Arrays.stream(ModelProviderResponse.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("authConfigJson");
        verify(service).getModelProvider(10L);
    }

    private ModelProvider provider() {
        ModelProvider provider = new ModelProvider();
        provider.setId(10L);
        provider.setTenantId(100L);
        provider.setProviderCode("openai-prod");
        provider.setProviderName("OpenAI Production");
        provider.setProviderType("openai_compatible");
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setAuthConfigJson("{\"apiKey\":\"sk-live-secret\"}");
        provider.setStatus("active");
        return provider;
    }
}
