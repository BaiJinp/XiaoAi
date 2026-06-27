package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.model.SubmitCollaborationPlanCommand;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationPlanControllerTest {

    private final CollaborationPlanService service = mock(CollaborationPlanService.class);
    private final CollaborationPlanController controller = new CollaborationPlanController(service);

    @Test
    void listPlansShouldDelegateWithSessionId() {
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(20L);
        plan.setSessionId(10L);
        plan.setValidationStatus("passed");
        when(service.listPlans(10L)).thenReturn(List.of(plan));

        ApiResponse<List<CollaborationPlan>> result = controller.listPlans(10L);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getSessionId()).isEqualTo(10L);
        verify(service).listPlans(10L);
    }

    @Test
    void submitPlanShouldUseSessionIdFromPath() {
        SubmitCollaborationPlanCommand command = new SubmitCollaborationPlanCommand();
        command.setPlanJson("{\"goal\":\"通用协作\",\"stages\":[{}]}");
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(20L);
        plan.setSessionId(10L);
        plan.setValidationStatus("passed");
        when(service.submitPlan(command)).thenReturn(plan);

        ApiResponse<CollaborationPlan> result = controller.submitPlan(10L, command);

        assertThat(command.getSessionId()).isEqualTo(10L);
        assertThat(result.getData().getValidationStatus()).isEqualTo("passed");
        verify(service).submitPlan(command);
    }

    @Test
    void validatePlanShouldReturnTenantScopedPlan() {
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(20L);
        plan.setSessionId(10L);
        plan.setValidationStatus("passed");
        when(service.getPlan(20L)).thenReturn(plan);

        ApiResponse<CollaborationPlan> result = controller.validatePlan(10L, 20L);

        assertThat(result.getData().getId()).isEqualTo(20L);
        verify(service).getPlan(20L);
    }
}
