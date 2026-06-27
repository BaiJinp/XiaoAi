package com.xiaoai.agent.plugin.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.plugin.model.ImportPluginManifestCommand;
import com.xiaoai.agent.plugin.model.PluginManifestResponse;
import com.xiaoai.agent.plugin.service.PluginManifestService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginManifestControllerTest {

    private final PluginManifestService pluginManifestService = mock(PluginManifestService.class);
    private final PluginManifestController controller = new PluginManifestController(pluginManifestService);

    @Test
    void importManifestShouldDelegateToService() {
        ImportPluginManifestCommand command = new ImportPluginManifestCommand();
        command.setManifestJson("{}");
        when(pluginManifestService.importManifest(command)).thenReturn(PluginManifestResponse.builder()
                .pluginId(1L)
                .pluginCode("project-cli")
                .tools(List.of())
                .build());

        ApiResponse<PluginManifestResponse> response = controller.importManifest(command);

        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getData().getPluginCode()).isEqualTo("project-cli");
        verify(pluginManifestService).importManifest(command);
    }

    @Test
    void listManifestsShouldDelegateToService() {
        when(pluginManifestService.listManifests()).thenReturn(List.of());

        ApiResponse<List<PluginManifestResponse>> response = controller.listManifests();

        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getData()).isEmpty();
        verify(pluginManifestService).listManifests();
    }
}
