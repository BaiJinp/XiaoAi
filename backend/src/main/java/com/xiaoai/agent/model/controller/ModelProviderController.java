package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.model.model.ModelProviderResponse;
import com.xiaoai.agent.model.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import com.xiaoai.agent.model.model.CreateModelProviderCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/model-providers")
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    @PostMapping
public ApiResponse<ModelProviderResponse> create(@Valid @RequestBody CreateModelProviderCommand command) {
        return ApiResponse.success(ModelProviderResponse.from(modelProviderService.createModelProvider(command)));
    }

    @GetMapping("/{id}")
public ApiResponse<ModelProviderResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(ModelProviderResponse.from(modelProviderService.getModelProvider(id)));
    }
}
