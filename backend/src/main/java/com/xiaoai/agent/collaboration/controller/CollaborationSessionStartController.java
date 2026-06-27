package com.xiaoai.agent.collaboration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.model.StartCollaborationSessionCommand;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategy;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyRegistry;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration-sessions/{sessionId}")
public class CollaborationSessionStartController {

    private final CollaborationSessionService collaborationSessionService;
    private final CollaborationStrategyRegistry collaborationStrategyRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/start")
public ApiResponse<CollaborationStrategyResult> startSession(@PathVariable Long sessionId,
                                                                 @Valid @RequestBody StartCollaborationSessionCommand command) {
        CollaborationSession session = collaborationSessionService.getSession(sessionId);
        CollaborationStrategy strategy = collaborationStrategyRegistry.getStrategy(session.getStrategyType());
        CollaborationStrategyResult result = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(sessionId)
                .planId(command.getPlanId())
                .build());
        session.setContextJson(mergeActivePlanId(session.getContextJson(), command.getPlanId()));
        session.setStatus("running");
        session.setCurrentStageCode(result.getCurrentStageCode());
        collaborationSessionService.updateById(session);
        return ApiResponse.success(result);
    }

    private String mergeActivePlanId(String contextJson, Long planId) {
        ObjectNode context = objectMapper.createObjectNode();
        if (contextJson != null && !contextJson.isBlank()) {
            try {
                JsonNode existing = objectMapper.readTree(contextJson);
                if (existing != null && existing.isObject()) {
                    context.setAll((ObjectNode) existing);
                }
            } catch (Exception ignored) {
            }
        }
        context.put("activePlanId", planId);
        return context.toString();
    }
}
