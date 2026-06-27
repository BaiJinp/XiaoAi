package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.service.ModelCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/model-call-logs")
public class ModelCallLogController {

    private final ModelCallLogService modelCallLogService;

    @GetMapping("/{id}")
public ApiResponse<ModelCallLog> getById(@PathVariable Long id) {
        return ApiResponse.success(modelCallLogService.getModelCallLog(id));
    }
}
