package com.xiaoai.agent.runtime.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.runtime.entity.RuntimeNode;
import com.xiaoai.agent.runtime.service.RuntimeNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/runtime-nodes")
public class RuntimeNodeController {

    private final RuntimeNodeService runtimeNodeService;

    @GetMapping("/{id}")
public ApiResponse<RuntimeNode> getById(@PathVariable Long id) {
        return ApiResponse.success(runtimeNodeService.getById(id));
    }
}
