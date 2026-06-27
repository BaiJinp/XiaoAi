package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.mapper.AgentRoleMapper;
import com.xiaoai.agent.collaboration.model.UpdateAgentRoleDefaultAgentCommand;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRoleServiceImplTest {

    private final AgentRoleMapper agentRoleMapper = mock(AgentRoleMapper.class);
    private final AgentService agentService = mock(AgentService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final AgentRoleServiceImpl agentRoleService = new AgentRoleServiceImpl(agentService, agentVersionService);

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(agentRoleService, agentRoleMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-agent-role")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getRoleShouldReturnTenantScopedRole() {
        AgentRole role = new AgentRole();
        role.setId(1L);
        role.setTenantId(100L);
        role.setRoleCode("generic_analyst");
        role.setRoleName("通用分析师");
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role);

        AgentRole result = agentRoleService.getRole(1L);

        verify(agentRoleMapper).selectOne(any(Wrapper.class));
        assertThat(result.getRoleCode()).isEqualTo("generic_analyst");
        assertThat(result.getRoleName()).isEqualTo("通用分析师");
    }

    @Test
    void getRoleShouldRejectMissingOrCrossTenantRole() {
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> agentRoleService.getRole(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent role not found");
    }

    @Test
    void listActiveRolesShouldReturnTenantActiveRolesByDomain() {
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setTenantId(100L);
        role.setRoleCode("software_product_manager");
        role.setRoleName("Product Manager");
        role.setDomainCode("software_development");
        role.setDefaultAgentId(12L);
        role.setDefaultAgentVersionId(13L);
        role.setStatus("active");
        when(agentRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));

        List<AgentRole> result = agentRoleService.listActiveRoles("software_development");

        verify(agentRoleMapper).selectList(any(Wrapper.class));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoleCode()).isEqualTo("software_product_manager");
        assertThat(result.get(0).getDomainCode()).isEqualTo("software_development");
        assertThat(result.get(0).getDefaultAgentId()).isEqualTo(12L);
        assertThat(result.get(0).getDefaultAgentVersionId()).isEqualTo(13L);
    }

    @Test
    void updateDefaultAgentShouldValidateVersionAndPersistBinding() {
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setTenantId(100L);
        role.setRoleCode("software_product_manager");
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(agentService.getAgent(12L)).thenReturn(new Agent());
        AgentVersion version = new AgentVersion();
        version.setId(13L);
        version.setAgentId(12L);
        when(agentVersionService.getVersion(13L)).thenReturn(version);
        UpdateAgentRoleDefaultAgentCommand command = new UpdateAgentRoleDefaultAgentCommand();
        command.setDefaultAgentId(12L);
        command.setDefaultAgentVersionId(13L);

        AgentRole result = agentRoleService.updateDefaultAgent(2L, command);

        verify(agentService).getAgent(12L);
        verify(agentVersionService).getVersion(13L);
        verify(agentRoleMapper).update(any(), any(Wrapper.class));
        assertThat(result.getDefaultAgentId()).isEqualTo(12L);
        assertThat(result.getDefaultAgentVersionId()).isEqualTo(13L);
    }

    @Test
    void updateDefaultAgentShouldAllowClearingBinding() {
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setTenantId(100L);
        role.setDefaultAgentId(12L);
        role.setDefaultAgentVersionId(13L);
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role);

        AgentRole result = agentRoleService.updateDefaultAgent(2L, new UpdateAgentRoleDefaultAgentCommand());

        verify(agentRoleMapper).update(any(), any(Wrapper.class));
        assertThat(result.getDefaultAgentId()).isNull();
        assertThat(result.getDefaultAgentVersionId()).isNull();
    }

    @Test
    void updateDefaultAgentShouldRequireAgentAndVersionTogether() {
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setTenantId(100L);
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        UpdateAgentRoleDefaultAgentCommand command = new UpdateAgentRoleDefaultAgentCommand();
        command.setDefaultAgentId(12L);

        assertThatThrownBy(() -> agentRoleService.updateDefaultAgent(2L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Default agent and version must be set together");
    }

    @Test
    void updateDefaultAgentShouldRejectVersionFromAnotherAgent() {
        AgentRole role = new AgentRole();
        role.setId(2L);
        role.setTenantId(100L);
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(agentService.getAgent(12L)).thenReturn(new Agent());
        AgentVersion version = new AgentVersion();
        version.setId(13L);
        version.setAgentId(99L);
        when(agentVersionService.getVersion(13L)).thenReturn(version);
        UpdateAgentRoleDefaultAgentCommand command = new UpdateAgentRoleDefaultAgentCommand();
        command.setDefaultAgentId(12L);
        command.setDefaultAgentVersionId(13L);

        assertThatThrownBy(() -> agentRoleService.updateDefaultAgent(2L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent version does not belong to default agent");
    }
}
