package com.xiaoai.agent.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.mapper.ModelProviderMapper;
import com.xiaoai.agent.model.model.CreateModelProviderCommand;
import org.mockito.ArgumentCaptor;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProviderServiceImplTest {

    private final ModelProviderMapper modelProviderMapper = mock(ModelProviderMapper.class);
    private final ModelProviderServiceImpl modelProviderService = new ModelProviderServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(modelProviderService, modelProviderMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createModelProviderShouldPersistTenantScopedProviderWithMaskedApiKey() {
        when(modelProviderMapper.insert(any(ModelProvider.class))).thenAnswer(invocation -> {
            ModelProvider provider = invocation.getArgument(0);
            provider.setId(10L);
            return 1;
        });
        CreateModelProviderCommand command = new CreateModelProviderCommand();
        command.setProviderCode("openai-prod");
        command.setProviderName("OpenAI Production");
        command.setProviderType("openai_compatible");
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey("sk-live-secret");

        ModelProvider result = modelProviderService.createModelProvider(command);

        ArgumentCaptor<ModelProvider> captor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(modelProviderMapper).insert(captor.capture());
        ModelProvider saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getProviderCode()).isEqualTo("openai-prod");
        assertThat(saved.getProviderName()).isEqualTo("OpenAI Production");
        assertThat(saved.getProviderType()).isEqualTo("openai_compatible");
        assertThat(saved.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(saved.getAuthConfigJson()).contains("apiKey").contains("sk-live-secret");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void createModelProviderShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        CreateModelProviderCommand command = new CreateModelProviderCommand();
        command.setProviderCode("openai-prod");
        command.setProviderName("OpenAI Production");
        command.setProviderType("openai_compatible");
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey("sk-live-secret");

        assertThatThrownBy(() -> modelProviderService.createModelProvider(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(modelProviderMapper, never()).insert(any(ModelProvider.class));
    }

    @Test
    void getModelProviderShouldReturnTenantScopedProvider() {
        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setTenantId(100L);
        provider.setProviderName("mock");
        when(modelProviderMapper.selectOne(any(Wrapper.class))).thenReturn(provider);

        ModelProvider result = modelProviderService.getModelProvider(1L);

        assertThat(result.getProviderName()).isEqualTo("mock");
    }

    @Test
    void getModelProviderShouldRejectMissingOrCrossTenantProvider() {
        when(modelProviderMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> modelProviderService.getModelProvider(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model provider not found");
    }
}
