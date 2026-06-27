package com.xiaoai.agent.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.mapper.ModelConfigMapper;
import com.xiaoai.agent.model.model.CreateModelConfigCommand;
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

class ModelConfigServiceImplTest {

    private final ModelConfigMapper modelConfigMapper = mock(ModelConfigMapper.class);
    private final ModelConfigServiceImpl modelConfigService = new ModelConfigServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(modelConfigService, modelConfigMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createModelConfigShouldPersistTenantScopedModelConfig() {
        when(modelConfigMapper.insert(any(ModelConfig.class))).thenAnswer(invocation -> {
            ModelConfig model = invocation.getArgument(0);
            model.setId(11L);
            return 1;
        });
        CreateModelConfigCommand command = new CreateModelConfigCommand();
        command.setProviderId(10L);
        command.setModelCode("gpt-4o-mini");
        command.setModelName("GPT-4o Mini");
        command.setModelType("chat");
        command.setContextWindow(128000);
        command.setConfigJson("{\"temperature\":0.2}");

        ModelConfig result = modelConfigService.createModelConfig(command);

        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(modelConfigMapper).insert(captor.capture());
        ModelConfig saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getProviderId()).isEqualTo(10L);
        assertThat(saved.getModelCode()).isEqualTo("gpt-4o-mini");
        assertThat(saved.getModelName()).isEqualTo("GPT-4o Mini");
        assertThat(saved.getModelType()).isEqualTo("chat");
        assertThat(saved.getContextWindow()).isEqualTo(128000);
        assertThat(saved.getConfigJson()).isEqualTo("{\"temperature\":0.2}");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(result.getId()).isEqualTo(11L);
    }

    @Test
    void createModelConfigShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        CreateModelConfigCommand command = new CreateModelConfigCommand();
        command.setProviderId(10L);
        command.setModelCode("gpt-4o-mini");
        command.setModelName("GPT-4o Mini");
        command.setModelType("chat");

        assertThatThrownBy(() -> modelConfigService.createModelConfig(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(modelConfigMapper, never()).insert(any(ModelConfig.class));
    }

    @Test
    void getModelConfigShouldReturnTenantScopedModelConfig() {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setTenantId(100L);
        model.setModelName("mock-chat");
        when(modelConfigMapper.selectOne(any(Wrapper.class))).thenReturn(model);

        ModelConfig result = modelConfigService.getModelConfig(1L);

        assertThat(result.getModelName()).isEqualTo("mock-chat");
    }

    @Test
    void getModelConfigShouldRejectMissingOrCrossTenantModelConfig() {
        when(modelConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> modelConfigService.getModelConfig(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model config not found");
    }
}
