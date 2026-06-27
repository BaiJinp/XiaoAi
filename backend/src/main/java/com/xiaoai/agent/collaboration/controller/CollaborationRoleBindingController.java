package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.model.CollaborationRoleBindingResponse;
import com.xiaoai.agent.collaboration.model.UpdateCollaborationRoleBindingCommand;
import com.xiaoai.agent.collaboration.service.CollaborationRoleBindingService;
import com.xiaoai.agent.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration-templates/{templateId}/role-bindings")
public class CollaborationRoleBindingController {

    private final CollaborationRoleBindingService collaborationRoleBindingService;

    @GetMapping
    public ApiResponse<List<CollaborationRoleBindingResponse>> listTemplateBindings(@PathVariable Long templateId,
                                                                                    @RequestParam(required = false) String bindingScope,
                                                                                    @RequestParam(required = false) String bindingKey) {
        return ApiResponse.success(collaborationRoleBindingService.listTemplateBindings(templateId, bindingScope, bindingKey));
    }

    @PutMapping("/{roleCode}")
public ApiResponse<CollaborationRoleBindingResponse> updateTemplateBinding(@PathVariable Long templateId,
                                                                               @PathVariable String roleCode,
                                                                               @RequestBody UpdateCollaborationRoleBindingCommand command) {
        return ApiResponse.success(collaborationRoleBindingService.updateTemplateBinding(templateId, roleCode, command));
    }
}
