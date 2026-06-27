package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import com.xiaoai.agent.model.model.CreateModelConfigCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/model-configs")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @PostMapping
public ApiResponse<ModelConfig> create(@Valid @RequestBody CreateModelConfigCommand command) {
        return ApiResponse.success(modelConfigService.createModelConfig(command));
    }

    @GetMapping("/{id}")
public ApiResponse<ModelConfig> getById(@PathVariable Long id) {
        return ApiResponse.success(modelConfigService.getModelConfig(id));
    }
}
