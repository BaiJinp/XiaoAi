package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.service.AgentHandoffService;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
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
@RequestMapping("/api/v1/collaboration-sessions/{sessionId}/handoffs")
public class AgentHandoffController {

    private final AgentHandoffService agentHandoffService;

    @GetMapping
    public ApiResponse<List<AgentHandoff>> listHandoffs(@PathVariable Long sessionId) {
        return ApiResponse.success(agentHandoffService.listHandoffs(sessionId));
    }

    @PostMapping
public ApiResponse<AgentHandoff> createHandoff(@PathVariable Long sessionId,
                                                   @Valid @RequestBody CreateAgentHandoffCommand command) {
        command.setSessionId(sessionId);
        return ApiResponse.success(agentHandoffService.createHandoff(command));
    }

    @PostMapping("/{handoffId}/accept")
public ApiResponse<Void> acceptHandoff(@PathVariable Long sessionId,
                                           @PathVariable Long handoffId) {
        ensureHandoffInSession(sessionId, handoffId);
        agentHandoffService.acceptHandoff(handoffId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{handoffId}/reject")
public ApiResponse<Void> rejectHandoff(@PathVariable Long sessionId,
                                           @PathVariable Long handoffId) {
        ensureHandoffInSession(sessionId, handoffId);
        agentHandoffService.rejectHandoff(handoffId);
        return ApiResponse.success(null);
    }

    private void ensureHandoffInSession(Long sessionId, Long handoffId) {
        AgentHandoff handoff = agentHandoffService.getHandoff(handoffId);
        if (!sessionId.equals(handoff.getSessionId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent handoff must belong to session");
        }
    }
}
