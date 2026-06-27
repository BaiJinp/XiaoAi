package com.xiaoai.agent.policy.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.policy.entity.PolicyRule;
import com.xiaoai.agent.policy.mapper.PolicyRuleMapper;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyRuleServiceImplTest {

    private final PolicyRuleMapper policyRuleMapper = mock(PolicyRuleMapper.class);
    private final PolicyRuleServiceImpl policyRuleService = new PolicyRuleServiceImpl(new ObjectMapper());

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(policyRuleService, policyRuleMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-policy")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void evaluateShouldAllowWhenNoRuleMatched() {
        when(policyRuleMapper.selectList(any())).thenReturn(List.of());
        EvaluatePolicyCommand command = new EvaluatePolicyCommand();
        command.setTargetType("tool");
        command.setTargetId(10L);

        PolicyDecisionResponse response = policyRuleService.evaluate(command);

        assertThat(response.getEffect()).isEqualTo("allow");
        assertThat(response.getMatchedRuleId()).isNull();
        assertThat(response.getApproverUserIds()).isEmpty();
    }

    @Test
    void getPolicyRuleShouldReturnTenantScopedPolicyRule() {
        PolicyRule rule = new PolicyRule();
        rule.setId(1L);
        rule.setTenantId(100L);
        rule.setRuleName("高风险工具审批");
        when(policyRuleMapper.selectOne(any(Wrapper.class))).thenReturn(rule);

        PolicyRule result = policyRuleService.getPolicyRule(1L);

        assertThat(result.getRuleName()).isEqualTo("高风险工具审批");
    }

    @Test
    void getPolicyRuleShouldRejectMissingOrCrossTenantPolicyRule() {
        when(policyRuleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> policyRuleService.getPolicyRule(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Policy rule not found");
    }

    @Test
    void evaluateShouldRejectMissingTenantContext() {
        UserContextHolder.clear();
        EvaluatePolicyCommand command = new EvaluatePolicyCommand();
        command.setTargetType("tool");
        command.setTargetId(10L);

        assertThatThrownBy(() -> policyRuleService.evaluate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing tenant context");
    }

    @Test
    void evaluateShouldReturnMatchedApproveRuleAndApprovers() {
        PolicyRule rule = new PolicyRule();
        rule.setId(1L);
        rule.setRuleCode("POL-HIGH-RISK-TOOL");
        rule.setRuleName("高风险工具审批");
        rule.setEffect("approve");
        rule.setApproverJson("[300,301]");
        when(policyRuleMapper.selectList(any())).thenReturn(List.of(rule));
        EvaluatePolicyCommand command = new EvaluatePolicyCommand();
        command.setTargetType("tool");
        command.setTargetId(10L);
        command.setFallbackApproverUserId(999L);

        PolicyDecisionResponse response = policyRuleService.evaluate(command);

        assertThat(response.getEffect()).isEqualTo("approve");
        assertThat(response.getMatchedRuleId()).isEqualTo(1L);
        assertThat(response.getMatchedRuleCode()).isEqualTo("POL-HIGH-RISK-TOOL");
        assertThat(response.getApproverUserIds()).containsExactly(300L, 301L);
        assertThat(response.getReason()).isEqualTo("高风险工具审批");
    }

    @Test
    void evaluateShouldFallbackToCommandApproverWhenRuleApproverJsonInvalid() {
        PolicyRule rule = new PolicyRule();
        rule.setId(2L);
        rule.setRuleCode("POL-BAD-APPROVER");
        rule.setRuleName("审批人配置异常时使用兜底审批人");
        rule.setEffect("approve");
        rule.setApproverJson("not-json");
        when(policyRuleMapper.selectList(any())).thenReturn(List.of(rule));
        EvaluatePolicyCommand command = new EvaluatePolicyCommand();
        command.setTargetType("tool");
        command.setTargetId(10L);
        command.setFallbackApproverUserId(999L);

        PolicyDecisionResponse response = policyRuleService.evaluate(command);

        assertThat(response.getEffect()).isEqualTo("approve");
        assertThat(response.getApproverUserIds()).containsExactly(999L);
        assertThat(response.getMatchedRuleCode()).isEqualTo("POL-BAD-APPROVER");
    }

    @Test
    void evaluateShouldKeepEmptyApproversWhenNoRuleOrFallbackApproverConfigured() {
        PolicyRule rule = new PolicyRule();
        rule.setId(3L);
        rule.setRuleCode("POL-MISSING-APPROVER");
        rule.setRuleName("未配置审批人");
        rule.setEffect("approve");
        rule.setApproverJson("[]");
        when(policyRuleMapper.selectList(any())).thenReturn(List.of(rule));
        EvaluatePolicyCommand command = new EvaluatePolicyCommand();
        command.setTargetType("tool");
        command.setTargetId(10L);

        PolicyDecisionResponse response = policyRuleService.evaluate(command);

        assertThat(response.getEffect()).isEqualTo("approve");
        assertThat(response.getApproverUserIds()).isEmpty();
        assertThat(response.getMatchedRuleId()).isEqualTo(3L);
    }

    @Test
    void evaluateShouldReturnDenyEffectFromMatchedRule() {
        PolicyRule rule = new PolicyRule();
        rule.setId(4L);
        rule.setRuleCode("POL-DENY-TOOL");
        rule.setRuleName("禁止调用外部工具");
        rule.setEffect("deny");
        when(policyRuleMapper.selectList(any())).thenReturn(List.of(rule));
        EvaluatePolicyCommand command = new EvaluatePolicyCommand();
        command.setTargetType("tool");
        command.setTargetId(10L);

        PolicyDecisionResponse response = policyRuleService.evaluate(command);

        assertThat(response.getEffect()).isEqualTo("deny");
        assertThat(response.getMatchedRuleCode()).isEqualTo("POL-DENY-TOOL");
        assertThat(response.getApproverUserIds()).isEmpty();
    }
}

