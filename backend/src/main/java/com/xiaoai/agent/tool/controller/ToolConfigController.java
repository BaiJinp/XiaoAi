package com.xiaoai.agent.tool.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.EvaluateToolCallCommand;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallDecisionResponse;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolConfigService;
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
@RequestMapping("/api/v1/tool-configs")
public class ToolConfigController {

    private final ToolConfigService toolConfigService;

    @GetMapping("/{id}")
public ApiResponse<ToolConfig> getById(@PathVariable Long id) {
        return ApiResponse.success(toolConfigService.getToolConfig(id));
    }

    @PostMapping("/evaluate-call")
public ApiResponse<ToolCallDecisionResponse> evaluateToolCall(@Valid @RequestBody EvaluateToolCallCommand command) {
        return ApiResponse.success(toolConfigService.evaluateToolCall(command));
    }

    @PostMapping("/execute-call")
public ApiResponse<ToolCallExecuteResponse> executeToolCall(@Valid @RequestBody ExecuteToolCallCommand command) {
        if (Boolean.TRUE.equals(command.getApprovalBypassed())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "approvalBypassed is only allowed for runtime resume");
        }
        return ApiResponse.success(toolConfigService.executeToolCall(command));
    }
}
