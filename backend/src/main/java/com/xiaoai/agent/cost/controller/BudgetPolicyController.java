package com.xiaoai.agent.cost.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.cost.entity.BudgetPolicy;
import com.xiaoai.agent.cost.service.BudgetPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/budget-policys")
public class BudgetPolicyController {

    private final BudgetPolicyService budgetPolicyService;

    @GetMapping("/{id}")
public ApiResponse<BudgetPolicy> getById(@PathVariable Long id) {
        return ApiResponse.success(budgetPolicyService.getById(id));
    }
}
