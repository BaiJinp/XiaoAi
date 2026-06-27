package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.model.CreateModelConfigCommand;
import com.xiaoai.agent.model.service.ModelConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigControllerTest {

    private final ModelConfigService service = mock(ModelConfigService.class);
    private final ModelConfigController controller = new ModelConfigController(service);

    @Test
    void createShouldDelegateToService() {
        CreateModelConfigCommand command = new CreateModelConfigCommand();
        command.setProviderId(10L);
        command.setModelCode("gpt-4o-mini");
        command.setModelName("GPT-4o Mini");
        command.setModelType("chat");
        ModelConfig model = new ModelConfig();
        model.setId(11L);
        model.setModelCode("gpt-4o-mini");
        when(service.createModelConfig(command)).thenReturn(model);

        ApiResponse<ModelConfig> result = controller.create(command);

        assertThat(result.getCode()).isEqualTo("0");
        assertThat(result.getData().getId()).isEqualTo(11L);
        verify(service).createModelConfig(command);
    }
}
