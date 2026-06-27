package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.entity.CollaborationRoleBinding;
import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import com.xiaoai.agent.collaboration.mapper.AgentRoleMapper;
import com.xiaoai.agent.collaboration.mapper.CollaborationRoleBindingMapper;
import com.xiaoai.agent.collaboration.model.CollaborationRoleBindingResponse;
import com.xiaoai.agent.collaboration.model.UpdateCollaborationRoleBindingCommand;
import com.xiaoai.agent.collaboration.service.CollaborationRoleBindingService;
import com.xiaoai.agent.collaboration.service.CollaborationTemplateService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CollaborationRoleBindingServiceImpl extends ServiceImpl<CollaborationRoleBindingMapper, CollaborationRoleBinding>
        implements CollaborationRoleBindingService {

    static final String DEFAULT_BINDING_SCOPE = "template";
    static final String DEFAULT_BINDING_KEY = "default";

    private final CollaborationTemplateService collaborationTemplateService;
    private final AgentRoleMapper agentRoleMapper;
    private final AgentService agentService;
    private final AgentVersionService agentVersionService;

    public CollaborationRoleBindingServiceImpl() {
        this(null, null, null, null);
    }

    @Autowired
    public CollaborationRoleBindingServiceImpl(CollaborationTemplateService collaborationTemplateService,
                                               AgentRoleMapper agentRoleMapper,
                                               AgentService agentService,
                                               AgentVersionService agentVersionService) {
        this.collaborationTemplateService = collaborationTemplateService;
        this.agentRoleMapper = agentRoleMapper;
        this.agentService = agentService;
        this.agentVersionService = agentVersionService;
    }

    @Override
public List<CollaborationRoleBindingResponse> listTemplateBindings(Long templateId, String bindingScope, String bindingKey) {
        Long tenantId = UserContextHolder.requireTenantId();
        CollaborationTemplate template = requireTemplate(templateId);
        String normalizedScope = normalizedBindingScope(bindingScope);
        String normalizedKey = normalizedBindingKey(bindingKey);
        List<AgentRole> roles = listTemplateRoles(tenantId, template);
        Map<String, CollaborationRoleBinding> bindingByRoleCode = getBaseMapper().selectList(new LambdaQueryWrapper<CollaborationRoleBinding>()
                        .eq(CollaborationRoleBinding::getTenantId, tenantId)
                        .eq(CollaborationRoleBinding::getTemplateId, templateId)
                        .eq(CollaborationRoleBinding::getBindingScope, normalizedScope)
                        .eq(CollaborationRoleBinding::getBindingKey, normalizedKey)
                        .eq(CollaborationRoleBinding::getStatus, "active"))
                .stream()
                .collect(Collectors.toMap(CollaborationRoleBinding::getRoleCode, Function.identity(), (left, right) -> left));
        return roles.stream()
                .map(role -> CollaborationRoleBindingResponse.from(
                        role,
                        bindingByRoleCode.get(role.getRoleCode()),
                        templateId,
                        normalizedScope,
                        normalizedKey))
                .toList();
    }

    @Override
public CollaborationRoleBindingResponse updateTemplateBinding(Long templateId,
                                                                  String roleCode,
                                                                  UpdateCollaborationRoleBindingCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        CollaborationTemplate template = requireTemplate(templateId);
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Role code is required");
        }
        AgentRole role = requireTemplateRole(tenantId, template, roleCode);
        String normalizedScope = normalizedBindingScope(command == null ? null : command.getBindingScope());
        String normalizedKey = normalizedBindingKey(command == null ? null : command.getBindingKey());
        Long agentId = command == null ? null : command.getAgentId();
        Long agentVersionId = command == null ? null : command.getAgentVersionId();
        if ((agentId == null) != (agentVersionId == null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Template binding agent and version must be set together");
        }
        if (agentId != null) {
            validateAgentVersion(agentId, agentVersionId);
        }

        CollaborationRoleBinding existing = getBaseMapper().selectOne(new LambdaQueryWrapper<CollaborationRoleBinding>()
                .eq(CollaborationRoleBinding::getTenantId, tenantId)
                .eq(CollaborationRoleBinding::getTemplateId, templateId)
                .eq(CollaborationRoleBinding::getRoleCode, role.getRoleCode())
                .eq(CollaborationRoleBinding::getBindingScope, normalizedScope)
                .eq(CollaborationRoleBinding::getBindingKey, normalizedKey)
                .last("limit 1"));
        CollaborationRoleBinding saved = existing == null ? createBinding(tenantId, templateId, role.getRoleCode(), normalizedScope, normalizedKey) : existing;
        saved.setAgentId(agentId);
        saved.setAgentVersionId(agentVersionId);
        saved.setStatus(agentId == null ? "inactive" : "active");
        if (existing == null) {
            save(saved);
        } else {
            update(new UpdateWrapper<CollaborationRoleBinding>()
                    .eq("tenant_id", tenantId)
                    .eq("id", existing.getId())
                    .set("agent_id", agentId)
                    .set("agent_version_id", agentVersionId)
                    .set("status", saved.getStatus()));
        }
        CollaborationRoleBinding activeBinding = "active".equals(saved.getStatus()) ? saved : null;
        return CollaborationRoleBindingResponse.from(role, activeBinding, templateId, normalizedScope, normalizedKey);
    }

    private CollaborationTemplate requireTemplate(Long templateId) {
        if (templateId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration template id is required");
        }
        if (collaborationTemplateService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Collaboration template validator is not available");
        }
        return collaborationTemplateService.getTemplate(templateId);
    }

    private List<AgentRole> listTemplateRoles(Long tenantId, CollaborationTemplate template) {
        if (agentRoleMapper == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent role mapper is not available");
        }
        return agentRoleMapper.selectList(new LambdaQueryWrapper<AgentRole>()
                .eq(AgentRole::getTenantId, tenantId)
                .eq(AgentRole::getStatus, "active")
                .eq(StringUtils.hasText(template.getDomainCode()), AgentRole::getDomainCode, template.getDomainCode())
                .orderByAsc(AgentRole::getDomainCode)
                .orderByAsc(AgentRole::getRoleCode));
    }

    private AgentRole requireTemplateRole(Long tenantId, CollaborationTemplate template, String roleCode) {
        if (agentRoleMapper == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent role mapper is not available");
        }
        AgentRole role = agentRoleMapper.selectOne(new LambdaQueryWrapper<AgentRole>()
                .eq(AgentRole::getTenantId, tenantId)
                .eq(AgentRole::getRoleCode, roleCode)
                .eq(AgentRole::getStatus, "active")
                .eq(StringUtils.hasText(template.getDomainCode()), AgentRole::getDomainCode, template.getDomainCode())
                .last("limit 1"));
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent role not found for collaboration template");
        }
        return role;
    }

    private void validateAgentVersion(Long agentId, Long agentVersionId) {
        if (agentService == null || agentVersionService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Collaboration role binding validator is not available");
        }
        agentService.getAgent(agentId);
        AgentVersion version = agentVersionService.getVersion(agentVersionId);
        if (!agentId.equals(version.getAgentId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent version does not belong to template binding agent");
        }
    }

    private CollaborationRoleBinding createBinding(Long tenantId,
                                                   Long templateId,
                                                   String roleCode,
                                                   String bindingScope,
                                                   String bindingKey) {
        CollaborationRoleBinding binding = new CollaborationRoleBinding();
        binding.setTenantId(tenantId);
        binding.setTemplateId(templateId);
        binding.setRoleCode(roleCode);
        binding.setBindingScope(bindingScope);
        binding.setBindingKey(bindingKey);
        return binding;
    }

    private String normalizedBindingScope(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_BINDING_SCOPE;
    }

    private String normalizedBindingKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_BINDING_KEY;
    }
}
