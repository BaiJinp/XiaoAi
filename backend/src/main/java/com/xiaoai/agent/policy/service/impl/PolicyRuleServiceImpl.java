package com.xiaoai.agent.policy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.policy.entity.PolicyRule;
import com.xiaoai.agent.policy.mapper.PolicyRuleMapper;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;
import com.xiaoai.agent.policy.service.PolicyRuleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyRuleServiceImpl extends ServiceImpl<PolicyRuleMapper, PolicyRule> implements PolicyRuleService {

    private final ObjectMapper objectMapper;

    public PolicyRuleServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
public PolicyRule getPolicyRule(Long policyRuleId) {
        Long tenantId = UserContextHolder.requireTenantId();
        PolicyRule rule = getBaseMapper().selectOne(new LambdaQueryWrapper<PolicyRule>()
                .eq(PolicyRule::getTenantId, tenantId)
                .eq(PolicyRule::getId, policyRuleId)
                .last("limit 1"));
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy rule not found");
        }
        return rule;
    }

    @Override
public PolicyDecisionResponse evaluate(EvaluatePolicyCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<PolicyRule> wrapper = new LambdaQueryWrapper<PolicyRule>()
                .eq(PolicyRule::getTenantId, tenantId)
                .eq(PolicyRule::getTargetType, command.getTargetType())
                .eq(PolicyRule::getStatus, "active")
                .orderByAsc(PolicyRule::getPriority)
                .orderByDesc(PolicyRule::getUpdatedAt);
        if (command.getTargetId() == null) {
            wrapper.isNull(PolicyRule::getTargetId);
        } else {
            wrapper.and(rule -> rule.isNull(PolicyRule::getTargetId)
                    .or()
                    .eq(PolicyRule::getTargetId, command.getTargetId()));
        }

        List<PolicyRule> rules = list(wrapper);
        if (rules.isEmpty()) {
            return PolicyDecisionResponse.builder()
                    .effect("allow")
                    .approverUserIds(List.of())
                    .reason("No active policy rule matched")
                    .build();
        }

        PolicyRule rule = rules.get(0);
        List<Long> approvers = resolveApprovers(rule.getApproverJson(), command.getFallbackApproverUserId());
        return PolicyDecisionResponse.builder()
                .effect(rule.getEffect())
                .matchedRuleId(rule.getId())
                .matchedRuleCode(rule.getRuleCode())
                .approverUserIds(approvers)
                .reason(rule.getRuleName())
                .build();
    }

    private List<Long> resolveApprovers(String approverJson, Long fallbackApproverUserId) {
        List<Long> approvers = new ArrayList<>();
        if (StringUtils.hasText(approverJson)) {
            try {
                approvers.addAll(objectMapper.readValue(approverJson, new TypeReference<List<Long>>() {
                }));
            } catch (Exception ignored) {
                // Invalid approver config falls back to the caller supplied enterprise approver.
            }
        }
        if (approvers.isEmpty() && fallbackApproverUserId != null) {
            approvers.add(fallbackApproverUserId);
        }
        return approvers;
    }
}
