package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.model.CollaborationRoleBindingResponse;
import com.xiaoai.agent.collaboration.model.UpdateCollaborationRoleBindingCommand;
import com.xiaoai.agent.collaboration.service.CollaborationRoleBindingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationRoleBindingControllerTest {

    private final CollaborationRoleBindingService service = mock(CollaborationRoleBindingService.class);
    private final CollaborationRoleBindingController controller = new CollaborationRoleBindingController(service);

    @Test
    void listTemplateBindingsShouldDelegateWithScopeAndKey() {
        when(service.listTemplateBindings(2L, "team", "team-a")).thenReturn(List.of(
                CollaborationRoleBindingResponse.builder()
                        .templateId(2L)
                        .roleCode("software_product_manager")
                        .effectiveAgentId(201L)
                        .effectiveAgentVersionId(2001L)
                        .source("template")
                        .build()
        ));

        var result = controller.listTemplateBindings(2L, "team", "team-a");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getEffectiveAgentId()).isEqualTo(201L);
        verify(service).listTemplateBindings(2L, "team", "team-a");
    }

    @Test
    void updateTemplateBindingShouldDelegateToService() {
        UpdateCollaborationRoleBindingCommand command = new UpdateCollaborationRoleBindingCommand();
        command.setAgentId(201L);
        command.setAgentVersionId(2001L);
        when(service.updateTemplateBinding(2L, "software_product_manager", command)).thenReturn(
                CollaborationRoleBindingResponse.builder()
                        .templateId(2L)
                        .roleCode("software_product_manager")
                        .effectiveAgentId(201L)
                        .effectiveAgentVersionId(2001L)
                        .source("template")
                        .build());

        var result = controller.updateTemplateBinding(2L, "software_product_manager", command);

        assertThat(result.getData().getEffectiveAgentVersionId()).isEqualTo(2001L);
        verify(service).updateTemplateBinding(2L, "software_product_manager", command);
    }
}
