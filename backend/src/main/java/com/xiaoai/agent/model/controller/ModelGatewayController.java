package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/model-gateway")
public class ModelGatewayController {

    private final ModelGateway modelGateway;

    @PostMapping("/chat")
public ApiResponse<ChatModelResponse> chat(@Valid @RequestBody ChatModelCommand command) {
        return ApiResponse.success(modelGateway.chat(command));
    }

    @PostMapping("/embedding")
public ApiResponse<EmbeddingModelResponse> embedding(@Valid @RequestBody EmbeddingModelCommand command) {
        return ApiResponse.success(modelGateway.embedding(command));
    }
}
