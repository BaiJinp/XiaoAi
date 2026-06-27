package com.xiaoai.agent.tool.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.tool.entity.ToolCallLog;
import com.xiaoai.agent.tool.service.ToolCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tool-call-logs")
public class ToolCallLogController {

    private final ToolCallLogService toolCallLogService;

    @GetMapping("/{id}")
public ApiResponse<ToolCallLog> getById(@PathVariable Long id) {
        return ApiResponse.success(toolCallLogService.getToolCallLog(id));
    }
}
