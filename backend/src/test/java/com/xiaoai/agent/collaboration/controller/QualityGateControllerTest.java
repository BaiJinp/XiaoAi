package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.model.UpdateQualityGateCommand;
import com.xiaoai.agent.collaboration.service.CollaborationGateAdvanceService;
import com.xiaoai.agent.collaboration.service.QualityGateService;
import com.xiaoai.agent.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityGateControllerTest {

    private final QualityGateService service = mock(QualityGateService.class);
    private final CollaborationGateAdvanceService gateAdvanceService = mock(CollaborationGateAdvanceService.class);
    private final QualityGateController controller = new QualityGateController(service, gateAdvanceService);

    @Test
    void createGateShouldUseSessionIdFromPath() {
        CreateQualityGateCommand command = new CreateQualityGateCommand();
        command.setGateCode("generic_gate");
        command.setGateName("Generic Gate");
        command.setGateType("manual_confirmation");
        QualityGate gate = gate("pending");
        when(service.createGate(command)).thenReturn(gate);

        ApiResponse<QualityGate> result = controller.createGate(10L, command);

        assertThat(command.getSessionId()).isEqualTo(10L);
        assertThat(result.getData().getStatus()).isEqualTo("pending");
    }

    @Test
    void passAndFailGateShouldDelegateToService() {
        UpdateQualityGateCommand pass = new UpdateQualityGateCommand();
        pass.setResultJson("{\"passed\":true}");
        UpdateQualityGateCommand fail = new UpdateQualityGateCommand();
        fail.setFailReason("missing artifact");
        when(service.passGate(10L, 30L, pass)).thenReturn(gate("passed"));
        when(service.failGate(10L, 30L, fail)).thenReturn(gate("failed"));

        ApiResponse<QualityGate> passed = controller.passGate(10L, 30L, pass);
        ApiResponse<QualityGate> failed = controller.failGate(10L, 30L, fail);

        assertThat(passed.getData().getStatus()).isEqualTo("passed");
        assertThat(failed.getData().getStatus()).isEqualTo("failed");
        verify(service).passGate(10L, 30L, pass);
        verify(gateAdvanceService).continueAfterGate(10L, passed.getData());
        verify(service).failGate(10L, 30L, fail);
        verify(gateAdvanceService).failAfterGate(10L, failed.getData());
    }

    @Test
    void listGatesShouldReturnSessionGates() {
        when(service.listGates(10L)).thenReturn(List.of(gate("pending")));

        ApiResponse<List<QualityGate>> result = controller.listGates(10L);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getStatus()).isEqualTo("pending");
    }

    private QualityGate gate(String status) {
        QualityGate gate = new QualityGate();
        gate.setId(30L);
        gate.setSessionId(10L);
        gate.setGateCode("generic_gate");
        gate.setStatus(status);
        return gate;
    }
}
