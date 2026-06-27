package com.xiaoai.agent.user.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.user.entity.UserIdentityMapping;
import com.xiaoai.agent.user.service.UserIdentityMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-identity-mappings")
public class UserIdentityMappingController {

    private final UserIdentityMappingService userIdentityMappingService;

    @GetMapping("/{id}")
public ApiResponse<UserIdentityMapping> getById(@PathVariable Long id) {
        return ApiResponse.success(userIdentityMappingService.getById(id));
    }
}
