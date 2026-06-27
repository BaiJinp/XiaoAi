package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.CollaborationRoleBinding;
import com.xiaoai.agent.collaboration.model.CollaborationRoleBindingResponse;
import com.xiaoai.agent.collaboration.model.UpdateCollaborationRoleBindingCommand;

import java.util.List;

public interface CollaborationRoleBindingService extends IService<CollaborationRoleBinding> {

    List<CollaborationRoleBindingResponse> listTemplateBindings(Long templateId, String bindingScope, String bindingKey);

    CollaborationRoleBindingResponse updateTemplateBinding(Long templateId,
                                                           String roleCode,
                                                           UpdateCollaborationRoleBindingCommand command);
}
