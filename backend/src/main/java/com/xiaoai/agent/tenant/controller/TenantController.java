package com.xiaoai.agent.tenant.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.tenant.entity.Tenant;
import com.xiaoai.agent.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/{id}")
public ApiResponse<Tenant> getById(@PathVariable Long id) {
        return ApiResponse.success(tenantService.getById(id));
    }
}
