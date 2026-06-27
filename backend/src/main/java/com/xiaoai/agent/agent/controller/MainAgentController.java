package com.xiaoai.agent.agent.controller;

import com.xiaoai.agent.agent.model.CreateMainAgentCommand;
import com.xiaoai.agent.agent.model.MainAgentPreviewResponse;
import com.xiaoai.agent.agent.model.MainAgentSetupResponse;
import com.xiaoai.agent.agent.model.MainAgentTrialRunCommand;
import com.xiaoai.agent.agent.model.MainAgentTrialRunResponse;
import com.xiaoai.agent.agent.service.MainAgentTemplateService;
import com.xiaoai.agent.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/main-agent")
public class MainAgentController {

    private final MainAgentTemplateService mainAgentTemplateService;

    @GetMapping("/preview")
public ApiResponse<MainAgentPreviewResponse> preview() {
        return ApiResponse.success(mainAgentTemplateService.preview());
    }

    @PostMapping("/draft")
public ApiResponse<MainAgentSetupResponse> createDraft(@Valid @RequestBody CreateMainAgentCommand command) {
        return ApiResponse.success(mainAgentTemplateService.createDraft(command));
    }

    @PostMapping("/trial-run")
public ApiResponse<MainAgentTrialRunResponse> trialRun(@Valid @RequestBody MainAgentTrialRunCommand command) {
        return ApiResponse.success(mainAgentTemplateService.trialRun(command));
    }
}
