package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.service.AgentHandoffService;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHandoffControllerTest {

    private final AgentHandoffService service = mock(AgentHandoffService.class);
    private final AgentHandoffController controller = new AgentHandoffController(service);

    @Test
    void createHandoffShouldUseSessionIdFromPath() {
        CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
        command.setArtifactId(30L);
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(40L);
        handoff.setStatus("pending");
        when(service.createHandoff(command)).thenReturn(handoff);

        ApiResponse<AgentHandoff> result = controller.createHandoff(10L, command);

        assertThat(command.getSessionId()).isEqualTo(10L);
        assertThat(result.getData().getStatus()).isEqualTo("pending");
        verify(service).createHandoff(command);
    }

    @Test
    void listHandoffsShouldReturnSessionHandoffs() {
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(40L);
        handoff.setSessionId(10L);
        handoff.setStatus("pending");
        when(service.listHandoffs(10L)).thenReturn(List.of(handoff));

        ApiResponse<List<AgentHandoff>> result = controller.listHandoffs(10L);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getStatus()).isEqualTo("pending");
    }

    @Test
    void acceptAndRejectHandoffShouldDelegateToService() {
        AgentHandoff acceptHandoff = new AgentHandoff();
        acceptHandoff.setId(40L);
        acceptHandoff.setSessionId(10L);
        AgentHandoff rejectHandoff = new AgentHandoff();
        rejectHandoff.setId(41L);
        rejectHandoff.setSessionId(10L);
        when(service.getHandoff(40L)).thenReturn(acceptHandoff);
        when(service.getHandoff(41L)).thenReturn(rejectHandoff);

        ApiResponse<Void> accepted = controller.acceptHandoff(10L, 40L);
        ApiResponse<Void> rejected = controller.rejectHandoff(10L, 41L);

        assertThat(accepted.getData()).isNull();
        assertThat(rejected.getData()).isNull();
        verify(service).getHandoff(40L);
        verify(service).getHandoff(41L);
        verify(service).acceptHandoff(40L);
        verify(service).rejectHandoff(41L);
    }

    @Test
    void acceptHandoffShouldRejectCrossSessionPath() {
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(40L);
        handoff.setSessionId(11L);
        when(service.getHandoff(40L)).thenReturn(handoff);

        assertThatThrownBy(() -> controller.acceptHandoff(10L, 40L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent handoff must belong to session");

        verify(service, never()).acceptHandoff(40L);
    }
}
