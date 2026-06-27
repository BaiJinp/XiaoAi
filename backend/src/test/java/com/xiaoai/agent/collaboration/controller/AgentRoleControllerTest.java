package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.model.UpdateAgentRoleDefaultAgentCommand;
import com.xiaoai.agent.collaboration.service.AgentRoleService;
import com.xiaoai.agent.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRoleControllerTest {

    private final AgentRoleService service = mock(AgentRoleService.class);
    private final AgentRoleController controller = new AgentRoleController(service);

    @Test
    void listRolesShouldDelegateWithDomainCode() {
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setRoleCode("software_product_manager");
        role.setRoleName("Product Manager");
        role.setDomainCode("software_development");
        role.setDefaultAgentId(12L);
        role.setDefaultAgentVersionId(13L);
        role.setStatus("active");
        when(service.listActiveRoles("software_development")).thenReturn(List.of(role));

        ApiResponse<List<AgentRole>> result = controller.listRoles("software_development");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getRoleCode()).isEqualTo("software_product_manager");
        assertThat(result.getData().get(0).getDefaultAgentId()).isEqualTo(12L);
        assertThat(result.getData().get(0).getDefaultAgentVersionId()).isEqualTo(13L);
        verify(service).listActiveRoles("software_development");
    }

    @Test
    void updateDefaultAgentShouldDelegateToService() {
        UpdateAgentRoleDefaultAgentCommand command = new UpdateAgentRoleDefaultAgentCommand();
        command.setDefaultAgentId(12L);
        command.setDefaultAgentVersionId(13L);
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setDefaultAgentId(12L);
        role.setDefaultAgentVersionId(13L);
        when(service.updateDefaultAgent(2L, command)).thenReturn(role);

        ApiResponse<AgentRole> result = controller.updateDefaultAgent(2L, command);

        assertThat(result.getData().getDefaultAgentId()).isEqualTo(12L);
        assertThat(result.getData().getDefaultAgentVersionId()).isEqualTo(13L);
        verify(service).updateDefaultAgent(2L, command);
    }
}
