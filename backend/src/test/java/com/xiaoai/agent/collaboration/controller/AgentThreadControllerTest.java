package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentThreadControllerTest {

    private final AgentThreadService service = mock(AgentThreadService.class);
    private final AgentThreadController controller = new AgentThreadController(service);

    @Test
    void createThreadShouldUseSessionIdFromPath() {
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setAgentId(30L);
        when(service.createThread(command)).thenReturn(AgentThreadResponse.builder()
                .threadId(40L)
                .threadCode("TH001")
                .status("pending")
                .build());

        ApiResponse<AgentThreadResponse> result = controller.createThread(10L, command);

        assertThat(command.getSessionId()).isEqualTo(10L);
        assertThat(result.getData().getThreadId()).isEqualTo(40L);
        verify(service).createThread(command);
    }

    @Test
    void listThreadsShouldReturnSessionThreads() {
        AgentThread thread = new AgentThread();
        thread.setId(40L);
        thread.setSessionId(10L);
        thread.setThreadName("Intake Review");
        when(service.listThreads(10L)).thenReturn(List.of(thread));

        ApiResponse<List<AgentThread>> result = controller.listThreads(10L);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getThreadName()).isEqualTo("Intake Review");
    }
}
