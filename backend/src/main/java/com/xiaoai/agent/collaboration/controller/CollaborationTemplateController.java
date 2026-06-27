package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.model.CollaborationTemplateResponse;
import com.xiaoai.agent.collaboration.service.CollaborationTemplateService;
import com.xiaoai.agent.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration-templates")
public class CollaborationTemplateController {

    private final CollaborationTemplateService collaborationTemplateService;

    @GetMapping
    public ApiResponse<List<CollaborationTemplateResponse>> listTemplates(@RequestParam(required = false) String domainCode) {
        return ApiResponse.success(collaborationTemplateService.listActiveTemplates(domainCode));
    }

    @GetMapping("/{id}")
public ApiResponse<CollaborationTemplateResponse> getTemplate(@PathVariable Long id) {
        return ApiResponse.success(CollaborationTemplateResponse.from(collaborationTemplateService.getTemplate(id)));
    }
}
