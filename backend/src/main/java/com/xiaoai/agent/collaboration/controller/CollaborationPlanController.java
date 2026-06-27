package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.model.SubmitCollaborationPlanCommand;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration-sessions/{sessionId}/plans")
public class CollaborationPlanController {

    private final CollaborationPlanService collaborationPlanService;

    @GetMapping
    public ApiResponse<List<CollaborationPlan>> listPlans(@PathVariable Long sessionId) {
        return ApiResponse.success(collaborationPlanService.listPlans(sessionId));
    }

    @PostMapping
public ApiResponse<CollaborationPlan> submitPlan(@PathVariable Long sessionId,
                                                     @Valid @RequestBody SubmitCollaborationPlanCommand command) {
        command.setSessionId(sessionId);
        return ApiResponse.success(collaborationPlanService.submitPlan(command));
    }

    @PostMapping("/{planId}/validate")
public ApiResponse<CollaborationPlan> validatePlan(@PathVariable Long sessionId, @PathVariable Long planId) {
        return ApiResponse.success(collaborationPlanService.getPlan(planId));
    }
}
