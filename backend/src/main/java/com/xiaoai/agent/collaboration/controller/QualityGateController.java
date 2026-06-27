package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.model.UpdateQualityGateCommand;
import com.xiaoai.agent.collaboration.service.CollaborationGateAdvanceService;
import com.xiaoai.agent.collaboration.service.QualityGateService;
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
@RequestMapping("/api/v1/collaboration-sessions/{sessionId}/gates")
public class QualityGateController {

    private final QualityGateService qualityGateService;
    private final CollaborationGateAdvanceService collaborationGateAdvanceService;

    @GetMapping
    public ApiResponse<List<QualityGate>> listGates(@PathVariable Long sessionId) {
        return ApiResponse.success(qualityGateService.listGates(sessionId));
    }

    @PostMapping
public ApiResponse<QualityGate> createGate(@PathVariable Long sessionId,
                                               @Valid @RequestBody CreateQualityGateCommand command) {
        command.setSessionId(sessionId);
        return ApiResponse.success(qualityGateService.createGate(command));
    }

    @PostMapping("/{gateId}/pass")
public ApiResponse<QualityGate> passGate(@PathVariable Long sessionId,
                                             @PathVariable Long gateId,
                                             @RequestBody UpdateQualityGateCommand command) {
        QualityGate gate = qualityGateService.passGate(sessionId, gateId, command);
        collaborationGateAdvanceService.continueAfterGate(sessionId, gate);
        return ApiResponse.success(gate);
    }

    @PostMapping("/{gateId}/fail")
public ApiResponse<QualityGate> failGate(@PathVariable Long sessionId,
                                             @PathVariable Long gateId,
                                             @RequestBody UpdateQualityGateCommand command) {
        QualityGate gate = qualityGateService.failGate(sessionId, gateId, command);
        collaborationGateAdvanceService.failAfterGate(sessionId, gate);
        return ApiResponse.success(gate);
    }
}
