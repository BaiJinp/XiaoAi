package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.model.CollaborationSessionPageQuery;
import com.xiaoai.agent.collaboration.model.CollaborationSessionResponse;
import com.xiaoai.agent.collaboration.model.CreateCollaborationSessionCommand;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration-sessions")
public class CollaborationSessionController {

    private final CollaborationSessionService collaborationSessionService;

    @PostMapping
public ApiResponse<CollaborationSessionResponse> createSession(@Valid @RequestBody CreateCollaborationSessionCommand command) {
        return ApiResponse.success(collaborationSessionService.createSession(command));
    }

    @GetMapping("/{id}")
public ApiResponse<CollaborationSession> getById(@PathVariable Long id) {
        return ApiResponse.success(collaborationSessionService.getSession(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<CollaborationSession>> pageSessions(CollaborationSessionPageQuery query) {
        return ApiResponse.success(collaborationSessionService.pageSessions(query));
    }
}
