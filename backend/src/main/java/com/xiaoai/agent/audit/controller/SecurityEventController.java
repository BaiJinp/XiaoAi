package com.xiaoai.agent.audit.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.audit.entity.SecurityEvent;
import com.xiaoai.agent.audit.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/security-events")
public class SecurityEventController {

    private final SecurityEventService securityEventService;

    @GetMapping("/{id}")
public ApiResponse<SecurityEvent> getById(@PathVariable Long id) {
        return ApiResponse.success(securityEventService.getById(id));
    }
}
