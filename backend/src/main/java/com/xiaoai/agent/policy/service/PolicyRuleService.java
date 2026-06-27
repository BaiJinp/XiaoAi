package com.xiaoai.agent.policy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.policy.entity.PolicyRule;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;

public interface PolicyRuleService extends IService<PolicyRule> {

    PolicyRule getPolicyRule(Long policyRuleId);

    PolicyDecisionResponse evaluate(EvaluatePolicyCommand command);
}
