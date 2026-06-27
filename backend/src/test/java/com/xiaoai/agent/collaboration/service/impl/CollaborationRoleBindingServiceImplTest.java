package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.entity.CollaborationRoleBinding;
import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import com.xiaoai.agent.collaboration.mapper.AgentRoleMapper;
import com.xiaoai.agent.collaboration.mapper.CollaborationRoleBindingMapper;
import com.xiaoai.agent.collaboration.model.UpdateCollaborationRoleBindingCommand;
import com.xiaoai.agent.collaboration.service.CollaborationTemplateService;
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

class CollaborationRoleBindingServiceImplTest {

    private final CollaborationRoleBindingMapper bindingMapper = mock(CollaborationRoleBindingMapper.class);
    private final AgentRoleMapper agentRoleMapper = mock(AgentRoleMapper.class);
    private final CollaborationTemplateService templateService = mock(CollaborationTemplateService.class);
    private final AgentService agentService = mock(AgentService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final CollaborationRoleBindingServiceImpl service = new CollaborationRoleBindingServiceImpl(
            templateService,
            agentRoleMapper,
            agentService,
            agentVersionService);

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(service, bindingMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-role-binding")
                .build());
        when(templateService.getTemplate(2L)).thenReturn(template());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void listTemplateBindingsShouldMergeTemplateBindingAndRoleDefaultFallback() {
        AgentRole product = role(10L, "software_product_manager", 101L, 1001L);
        AgentRole tester = role(11L, "software_tester", 103L, 1003L);
        CollaborationRoleBinding binding = binding(20L, "software_product_manager", 201L, 2001L, "active");
        when(agentRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(product, tester));
        when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));

        var result = service.listTemplateBindings(2L, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRoleCode()).isEqualTo("software_product_manager");
        assertThat(result.get(0).getEffectiveAgentId()).isEqualTo(201L);
        assertThat(result.get(0).getEffectiveAgentVersionId()).isEqualTo(2001L);
        assertThat(result.get(0).getSource()).isEqualTo("template");
        assertThat(result.get(1).getRoleCode()).isEqualTo("software_tester");
        assertThat(result.get(1).getEffectiveAgentId()).isEqualTo(103L);
        assertThat(result.get(1).getSource()).isEqualTo("role_default");
    }

    @Test
    void updateTemplateBindingShouldValidateVersionAndPersistActiveBinding() {
        AgentRole product = role(10L, "software_product_manager", 101L, 1001L);
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(product);
        when(bindingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(agentService.getAgent(201L)).thenReturn(new Agent());
        AgentVersion version = new AgentVersion();
        version.setId(2001L);
        version.setAgentId(201L);
        when(agentVersionService.getVersion(2001L)).thenReturn(version);
        UpdateCollaborationRoleBindingCommand command = new UpdateCollaborationRoleBindingCommand();
        command.setAgentId(201L);
        command.setAgentVersionId(2001L);

        var result = service.updateTemplateBinding(2L, "software_product_manager", command);

        verify(agentService).getAgent(201L);
        verify(agentVersionService).getVersion(2001L);
        verify(bindingMapper).insert(any(CollaborationRoleBinding.class));
        assertThat(result.getEffectiveAgentId()).isEqualTo(201L);
        assertThat(result.getEffectiveAgentVersionId()).isEqualTo(2001L);
        assertThat(result.getSource()).isEqualTo("template");
    }

    @Test
    void updateTemplateBindingShouldAllowClearingAndFallbackToRoleDefault() {
        AgentRole product = role(10L, "software_product_manager", 101L, 1001L);
        CollaborationRoleBinding existing = binding(20L, "software_product_manager", 201L, 2001L, "active");
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(product);
        when(bindingMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        var result = service.updateTemplateBinding(2L, "software_product_manager", new UpdateCollaborationRoleBindingCommand());

        verify(bindingMapper).update(any(), any(Wrapper.class));
        assertThat(result.getAgentId()).isNull();
        assertThat(result.getAgentVersionId()).isNull();
        assertThat(result.getEffectiveAgentId()).isEqualTo(101L);
        assertThat(result.getEffectiveAgentVersionId()).isEqualTo(1001L);
        assertThat(result.getSource()).isEqualTo("role_default");
    }

    @Test
    void updateTemplateBindingShouldRejectVersionFromAnotherAgent() {
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role(10L, "software_product_manager", 101L, 1001L));
        when(agentService.getAgent(201L)).thenReturn(new Agent());
        AgentVersion version = new AgentVersion();
        version.setId(2001L);
        version.setAgentId(999L);
        when(agentVersionService.getVersion(2001L)).thenReturn(version);
        UpdateCollaborationRoleBindingCommand command = new UpdateCollaborationRoleBindingCommand();
        command.setAgentId(201L);
        command.setAgentVersionId(2001L);

        assertThatThrownBy(() -> service.updateTemplateBinding(2L, "software_product_manager", command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent version does not belong to template binding agent");
    }

    private CollaborationTemplate template() {
        CollaborationTemplate template = new CollaborationTemplate();
        template.setId(2L);
        template.setTenantId(100L);
        template.setDomainCode("software_development");
        template.setStatus("active");
        return template;
    }

    private AgentRole role(Long roleId, String roleCode, Long defaultAgentId, Long defaultAgentVersionId) {
        AgentRole role = new AgentRole();
        role.setId(roleId);
        role.setTenantId(100L);
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setDomainCode("software_development");
        role.setDefaultAgentId(defaultAgentId);
        role.setDefaultAgentVersionId(defaultAgentVersionId);
        role.setStatus("active");
        return role;
    }

    private CollaborationRoleBinding binding(Long bindingId,
                                             String roleCode,
                                             Long agentId,
                                             Long agentVersionId,
                                             String status) {
        CollaborationRoleBinding binding = new CollaborationRoleBinding();
        binding.setId(bindingId);
        binding.setTenantId(100L);
        binding.setTemplateId(2L);
        binding.setRoleCode(roleCode);
        binding.setBindingScope("template");
        binding.setBindingKey("default");
        binding.setAgentId(agentId);
        binding.setAgentVersionId(agentVersionId);
        binding.setStatus(status);
        return binding;
    }
}
