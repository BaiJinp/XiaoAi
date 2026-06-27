package com.xiaoai.agent.user.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.user.entity.UserAccount;
import com.xiaoai.agent.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserAccountController {

    private final UserAccountService userAccountService;

    @GetMapping("/{id}")
public ApiResponse<UserAccount> getById(@PathVariable Long id) {
        return ApiResponse.success(userAccountService.getById(id));
    }
}
