package com.xiaoai.agent.policy.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.policy.entity.PolicyRule;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;
import com.xiaoai.agent.policy.service.PolicyRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/policy-rules")
public class PolicyRuleController {

    private final PolicyRuleService policyRuleService;

    @GetMapping("/{id}")
public ApiResponse<PolicyRule> getById(@PathVariable Long id) {
        return ApiResponse.success(policyRuleService.getPolicyRule(id));
    }

    @PostMapping("/evaluate")
public ApiResponse<PolicyDecisionResponse> evaluate(@Valid @RequestBody EvaluatePolicyCommand command) {
        return ApiResponse.success(policyRuleService.evaluate(command));
    }
}
