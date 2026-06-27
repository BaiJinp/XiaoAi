package com.xiaoai.agent.collaboration.model;

import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.entity.CollaborationRoleBinding;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollaborationRoleBindingResponse {

    private final Long roleId;

    private final String roleCode;

    private final String roleName;

    private final String domainCode;

    private final Long globalDefaultAgentId;

    private final Long globalDefaultAgentVersionId;

    private final Long bindingId;

    private final Long templateId;

    private final String bindingScope;

    private final String bindingKey;

    private final Long agentId;

    private final Long agentVersionId;

    private final Long effectiveAgentId;

    private final Long effectiveAgentVersionId;

    private final String source;

    private final String status;

    public static CollaborationRoleBindingResponse from(AgentRole role,
                                                        CollaborationRoleBinding binding,
                                                        Long templateId,
                                                        String bindingScope,
                                                        String bindingKey) {
        Long boundAgentId = binding == null ? null : binding.getAgentId();
        Long boundAgentVersionId = binding == null ? null : binding.getAgentVersionId();
        Long effectiveAgentId = boundAgentId == null ? role.getDefaultAgentId() : boundAgentId;
        Long effectiveAgentVersionId = boundAgentVersionId == null ? role.getDefaultAgentVersionId() : boundAgentVersionId;
        String source = boundAgentId != null && boundAgentVersionId != null
                ? "template"
                : effectiveAgentId != null && effectiveAgentVersionId != null ? "role_default" : "unbound";
        return CollaborationRoleBindingResponse.builder()
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .domainCode(role.getDomainCode())
                .globalDefaultAgentId(role.getDefaultAgentId())
                .globalDefaultAgentVersionId(role.getDefaultAgentVersionId())
                .bindingId(binding == null ? null : binding.getId())
                .templateId(templateId)
                .bindingScope(binding == null ? bindingScope : binding.getBindingScope())
                .bindingKey(binding == null ? bindingKey : binding.getBindingKey())
                .agentId(boundAgentId)
                .agentVersionId(boundAgentVersionId)
                .effectiveAgentId(effectiveAgentId)
                .effectiveAgentVersionId(effectiveAgentVersionId)
                .source(source)
                .status(binding == null ? "fallback" : binding.getStatus())
                .build();
    }
}
