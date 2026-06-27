package com.xiaoai.agent.approval.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.Agent;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.policy.entity.PolicyRule;
import com.xiaoai.agent.policy.service.PolicyRuleService;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.service.ToolConfigService;
import com.xiaoai.agent.user.entity.UserAccount;
import com.xiaoai.agent.user.service.UserAccountService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批路由规则引擎
 * 根据四级路由规则动态确定审批人
 *
 * 路由优先级：
 * 1. 权限策略指定（最高优先级）
 * 2. 资源所有者
 * 3. 业务负责人（Agent owner）
 * 4. 租户管理员（兜底）
 */
@Component
public class ApprovalRouter {

    private static final Logger log = LoggerFactory.getLogger(ApprovalRouter.class);

    private final PolicyRuleService policyRuleService;
    private final ToolConfigService toolConfigService;
    private final AgentService agentService;
    private final UserAccountService userAccountService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalRouter(PolicyRuleService policyRuleService,
                          ToolConfigService toolConfigService,
                          AgentService agentService,
                          UserAccountService userAccountService,
                          ObjectMapper objectMapper) {
        this.policyRuleService = policyRuleService;
        this.toolConfigService = toolConfigService;
        this.agentService = agentService;
        this.userAccountService = userAccountService;
        this.objectMapper = objectMapper;
    }

    /**
     * 路由审批请求，返回审批人
     *
     * @param context 审批路由上下文
     * @return 路由结果
     */
    public RoutingResult route(ApprovalRoutingContext context) {
        log.info("Routing approval request: type={}, tenantId={}, agentId={}",
                context.getApprovalType(), context.getTenantId(), context.getAgentId());

        // 1. 权限策略指定（最高优先级）
        Long policyApprover = routeByPolicy(context);
        if (policyApprover != null) {
            log.info("Approval routed by policy: approver={}", policyApprover);
            return new RoutingResult(policyApprover, "policy_rule", "权限策略指定");
        }

        // 2. 资源所有者
        Long resourceOwner = routeByResourceOwner(context);
        if (resourceOwner != null) {
            log.info("Approval routed by resource owner: approver={}", resourceOwner);
            return new RoutingResult(resourceOwner, "resource_owner", "资源所有者");
        }

        // 3. 业务负责人（Agent owner）
        Long agentOwner = routeByAgentOwner(context);
        if (agentOwner != null) {
            log.info("Approval routed by agent owner: approver={}", agentOwner);
            return new RoutingResult(agentOwner, "agent_owner", "业务负责人");
        }

        // 4. 租户管理员（兜底）
        Long tenantAdmin = routeByTenantAdmin(context);
        if (tenantAdmin != null) {
            log.info("Approval routed by tenant admin: approver={}", tenantAdmin);
            return new RoutingResult(tenantAdmin, "tenant_admin", "租户管理员（兜底）");
        }

        // 所有路由都失败
        log.error("Failed to route approval request: no approver found for type={}, tenantId={}",
                context.getApprovalType(), context.getTenantId());
        return RoutingResult.failed("No approver found");
    }

    /**
     * 1. 权限策略指定
     * 根据 PolicyRule 的 approverJson 字段确定审批人
     */
    private Long routeByPolicy(ApprovalRoutingContext context) {
        if (policyRuleService == null) {
            return null;
        }

        try {
            // 查询匹配的策略规则
            List<PolicyRule> rules = policyRuleService.list(
                    new LambdaQueryWrapper<PolicyRule>()
                            .eq(PolicyRule::getTenantId, context.getTenantId())
                            .eq(PolicyRule::getRuleType, "approval")
                            .eq(PolicyRule::getStatus, "active")
                            .orderByAsc(PolicyRule::getPriority)
            );

            for (PolicyRule rule : rules) {
                // 检查规则是否匹配当前审批类型
                if (matchesApprovalType(rule, context.getApprovalType())) {
                    // 检查规则是否匹配当前资源
                    if (matchesTarget(rule, context.getTargetType(), context.getTargetId())) {
                        Long approver = extractApproverFromPolicy(rule);
                        if (approver != null) {
                            return approver;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to route by policy", e);
        }

        return null;
    }

    /**
     * 2. 资源所有者
     * 根据工具/知识库等资源的创建者确定审批人
     */
    private Long routeByResourceOwner(ApprovalRoutingContext context) {
        if (context.getTargetId() == null) {
            return null;
        }

        try {
            if ("tool".equals(context.getTargetType())) {
                ToolConfig tool = toolConfigService.getToolConfig(context.getTargetId());
                if (tool != null && tool.getCreatedBy() != null) {
                    return tool.getCreatedBy();
                }
            }
            // 后续可以扩展其他资源类型（知识库、模型等）
        } catch (Exception e) {
            log.error("Failed to route by resource owner", e);
        }

        return null;
    }

    /**
     * 3. 业务负责人（Agent owner）
     * 根据 Agent 的 ownerUserId 确定审批人
     */
    private Long routeByAgentOwner(ApprovalRoutingContext context) {
        if (context.getAgentId() == null || agentService == null) {
            return null;
        }

        try {
            Agent agent = agentService.getById(context.getAgentId());
            if (agent != null && agent.getOwnerUserId() != null) {
                return agent.getOwnerUserId();
            }
        } catch (Exception e) {
            log.error("Failed to route by agent owner", e);
        }

        return null;
    }

    /**
     * 4. 租户管理员（兜底）
     * 查找租户的第一个管理员用户
     */
    private Long routeByTenantAdmin(ApprovalRoutingContext context) {
        if (userAccountService == null) {
            return null;
        }

        try {
            List<UserAccount> admins = userAccountService.list(
                    new LambdaQueryWrapper<UserAccount>()
                            .eq(UserAccount::getTenantId, context.getTenantId())
                            .eq(UserAccount::getStatus, "active")
                            .like(UserAccount::getRoleCodes, "admin")
                            .last("limit 1")
            );

            if (!admins.isEmpty()) {
                return admins.get(0).getId();
            }
        } catch (Exception e) {
            log.error("Failed to route by tenant admin", e);
        }

        return null;
    }

    /**
     * 检查规则是否匹配审批类型
     */
    private boolean matchesApprovalType(PolicyRule rule, String approvalType) {
        if (!StringUtils.hasText(approvalType)) {
            return true; // 未指定审批类型，匹配所有
        }

        try {
            if (StringUtils.hasText(rule.getConditionJson())) {
                JsonNode condition = objectMapper.readTree(rule.getConditionJson());
                if (condition.hasNonNull("approvalType")) {
                    return approvalType.equals(condition.get("approvalType").asText());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse condition JSON", e);
        }

        return true; // 无条件，匹配所有
    }

    /**
     * 检查规则是否匹配目标资源
     */
    private boolean matchesTarget(PolicyRule rule, String targetType, Long targetId) {
        if (!StringUtils.hasText(rule.getTargetType())) {
            return true; // 未指定目标类型，匹配所有
        }

        if (targetType != null && !targetType.equals(rule.getTargetType())) {
            return false;
        }

        if (rule.getTargetId() != null && targetId != null && !rule.getTargetId().equals(targetId)) {
            return false;
        }

        return true;
    }

    /**
     * 从策略规则中提取审批人
     */
    private Long extractApproverFromPolicy(PolicyRule rule) {
        if (!StringUtils.hasText(rule.getApproverJson())) {
            return null;
        }

        try {
            JsonNode approverNode = objectMapper.readTree(rule.getApproverJson());
            if (approverNode.isArray() && !approverNode.isEmpty()) {
                // 返回第一个审批人
                return approverNode.get(0).asLong();
            } else if (approverNode.isNumber()) {
                return approverNode.asLong();
            }
        } catch (Exception e) {
            log.error("Failed to parse approver JSON", e);
        }

        return null;
    }

    /**
     * 审批路由上下文
     */
    public static class ApprovalRoutingContext {
        private Long tenantId;
        private Long agentId;
        private Long applicantUserId;
        private String approvalType;
        private String targetType;
        private Long targetId;

        public ApprovalRoutingContext(Long tenantId, Long agentId, Long applicantUserId,
                                      String approvalType, String targetType, Long targetId) {
            this.tenantId = tenantId;
            this.agentId = agentId;
            this.applicantUserId = applicantUserId;
            this.approvalType = approvalType;
            this.targetType = targetType;
            this.targetId = targetId;
        }
public Long getTenantId() { return tenantId; }
public Long getAgentId() { return agentId; }
public Long getApplicantUserId() { return applicantUserId; }
public String getApprovalType() { return approvalType; }
public String getTargetType() { return targetType; }
public Long getTargetId() { return targetId; }
    }

    /**
     * 路由结果
     */
    public static class RoutingResult {
        private final boolean success;
        private final Long approverUserId;
        private final String routeType;
        private final String description;
        private final String errorMessage;

        public RoutingResult(Long approverUserId, String routeType, String description) {
            this.success = true;
            this.approverUserId = approverUserId;
            this.routeType = routeType;
            this.description = description;
            this.errorMessage = null;
        }

        private RoutingResult(String errorMessage) {
            this.success = false;
            this.approverUserId = null;
            this.routeType = null;
            this.description = null;
            this.errorMessage = errorMessage;
        }

        public static RoutingResult failed(String errorMessage) {
            return new RoutingResult(errorMessage);
        }
public boolean isSuccess() { return success; }
public Long getApproverUserId() { return approverUserId; }
public String getRouteType() { return routeType; }
public String getDescription() { return description; }
public String getErrorMessage() { return errorMessage; }
    }
}
