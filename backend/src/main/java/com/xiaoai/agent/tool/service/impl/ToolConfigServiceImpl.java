package com.xiaoai.agent.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.model.ApprovalRequestResponse;
import com.xiaoai.agent.approval.model.CreateApprovalRequestCommand;
import com.xiaoai.agent.approval.service.ApprovalRequestService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;
import com.xiaoai.agent.policy.service.PolicyRuleService;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.tool.entity.ToolCallLog;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.executor.ToolExecutorRegistry;
import com.xiaoai.agent.tool.mapper.ToolConfigMapper;
import com.xiaoai.agent.tool.model.EvaluateToolCallCommand;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallDecisionResponse;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolCallLogService;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ToolConfigServiceImpl extends ServiceImpl<ToolConfigMapper, ToolConfig> implements ToolConfigService {

    private final PolicyRuleService policyRuleService;
    private final ApprovalRequestService approvalRequestService;
    private final ToolCallLogService toolCallLogService;
    private final ToolExecutorRegistry toolExecutorRegistry;
    private final AgentVersionMapper agentVersionMapper;
    private final TaskEventRecordService taskEventRecordService;
    private final ObjectMapper objectMapper;

    public ToolConfigServiceImpl(PolicyRuleService policyRuleService,
                                 ApprovalRequestService approvalRequestService,
                                 ToolCallLogService toolCallLogService) {
        this(policyRuleService, approvalRequestService, toolCallLogService, ToolExecutorRegistry.defaultRegistry(), null, null, new ObjectMapper());
    }

    @Autowired
    public ToolConfigServiceImpl(PolicyRuleService policyRuleService,
                                 ApprovalRequestService approvalRequestService,
                                 ToolCallLogService toolCallLogService,
                                 ToolExecutorRegistry toolExecutorRegistry,
                                 AgentVersionMapper agentVersionMapper,
                                 TaskEventRecordService taskEventRecordService,
                                 ObjectMapper objectMapper) {
        this.policyRuleService = policyRuleService;
        this.approvalRequestService = approvalRequestService;
        this.toolCallLogService = toolCallLogService;
        this.toolExecutorRegistry = toolExecutorRegistry;
        this.agentVersionMapper = agentVersionMapper;
        this.taskEventRecordService = taskEventRecordService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
public ToolConfig getToolConfig(Long toolId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ToolConfig tool = getBaseMapper().selectOne(new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getTenantId, tenantId)
                .eq(ToolConfig::getId, toolId)
                .last("limit 1"));
        if (tool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tool config not found");
        }
        return tool;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public ToolCallDecisionResponse evaluateToolCall(EvaluateToolCallCommand command) {
        UserContextHolder.requireTenantId();
        ToolConfig tool = getToolConfig(command.getToolId());

        PolicyDecisionResponse policy = policyRuleService.evaluate(toPolicyCommand(command));
        if ("approve".equals(policy.getEffect())) {
            ApprovalRequestResponse approval = approvalRequestService.createApproval(toApprovalCommand(command, tool, policy));
            return ToolCallDecisionResponse.builder()
                    .decision("approve")
                    .riskLevel(tool.getRiskLevel())
                    .approvalRequestId(approval.getApprovalRequestId())
                    .approvalStatus(approval.getApprovalStatus())
                    .approverUserIds(policy.getApproverUserIds())
                    .reason(policy.getReason())
                    .build();
        }
        return ToolCallDecisionResponse.builder()
                .decision(policy.getEffect())
                .riskLevel(tool.getRiskLevel())
                .approverUserIds(policy.getApproverUserIds() == null ? List.of() : policy.getApproverUserIds())
                .reason(policy.getReason())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public ToolCallExecuteResponse executeToolCall(ExecuteToolCallCommand command) {
        UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        ToolConfig tool = getToolConfig(command.getToolId());
        if (!"active".equals(tool.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Tool config is not active");
        }
        validateAgentVersionToolScope(command, tool);
        if (Boolean.TRUE.equals(command.getApprovalBypassed())) {
            validateApprovedToolCall(command);
        } else {
            ToolCallDecisionResponse decision = evaluateToolCall(command);
            if (!"allow".equals(decision.getDecision())) {
                ToolCallLog log = saveCallLog(command, tool, "blocked", null, decision.getReason(), 0, decision.getApprovalRequestId());
                return ToolCallExecuteResponse.builder()
                        .status("blocked")
                        .toolCallLogId(log.getId())
                        .approvalRequestId(decision.getApprovalRequestId())
                        .toolType(tool.getToolType())
                        .toolCode(tool.getToolCode())
                        .riskLevel(tool.getRiskLevel())
                        .executorType(tool.getToolType())
                        .errorMessage(decision.getReason())
                        .build();
            }
        }

        long start = System.currentTimeMillis();
        try {
            String resultJson = executeAllowedTool(tool, command);
            ToolCallLog log = saveCallLog(command, tool, "success", resultJson, null, elapsedMs(start), null);
            return ToolCallExecuteResponse.builder()
                    .status("success")
                    .toolCallLogId(log.getId())
                    .toolType(tool.getToolType())
                    .toolCode(tool.getToolCode())
                    .riskLevel(tool.getRiskLevel())
                    .executorType(tool.getToolType())
                    .resultJson(resultJson)
                    .build();
        } catch (RuntimeException ex) {
            ToolCallLog log = saveCallLog(command, tool, "failed", null, ex.getMessage(), elapsedMs(start), null);
            return ToolCallExecuteResponse.builder()
                    .status("failed")
                    .toolCallLogId(log.getId())
                    .toolType(tool.getToolType())
                    .toolCode(tool.getToolCode())
                    .riskLevel(tool.getRiskLevel())
                    .executorType(tool.getToolType())
                    .errorMessage(ex.getMessage())
                    .build();
        }
    }

    private void validateAgentVersionToolScope(ExecuteToolCallCommand command, ToolConfig tool) {
        if (command.getAgentVersionId() == null) {
            return;
        }
        if (agentVersionMapper == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent version mapper is not available");
        }
        Long tenantId = UserContextHolder.requireTenantId();
        AgentVersion version = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getTenantId, tenantId)
                .eq(AgentVersion::getId, command.getAgentVersionId())
                .last("limit 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent version not found");
        }
        ToolScope scope = parseToolScope(version.getToolScopeJson());
        if (scope.isEmpty()) {
            return;
        }
        if (!scope.toolIds().contains(tool.getId()) && !scope.toolCodes().contains(tool.getToolCode())) {
            recordToolDenied(command, tool);
            throw new BusinessException(ErrorCode.FORBIDDEN, "Tool call denied by agent version tool scope");
        }
    }

    private void recordToolDenied(ExecuteToolCallCommand command, ToolConfig tool) {
        if (taskEventRecordService == null || command.getTaskId() == null || command.getRunId() == null) {
            return;
        }
        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(UserContextHolder.requireTenantId())
                .userId(command.getApplicantUserId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .traceId(UserContextHolder.get() == null ? null : UserContextHolder.get().getTraceId())
                .eventType("TOOL_DENIED")
                .eventSummary("Tool call denied by agent version tool scope")
                .payloadJson("{\"toolId\":" + tool.getId()
                        + ",\"toolCode\":\"" + safeJson(tool.getToolCode())
                        + "\",\"toolType\":\"" + safeJson(tool.getToolType())
                        + "\",\"riskLevel\":\"" + safeJson(tool.getRiskLevel())
                        + "\",\"agentVersionId\":" + command.getAgentVersionId()
                        + ",\"reason\":\"tool_not_in_agent_version_scope\"}")
                .occurredAt(OffsetDateTime.now())
                .build());
    }

    private ToolScope parseToolScope(String toolScopeJson) {
        if (toolScopeJson == null || toolScopeJson.isBlank()) {
            return ToolScope.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(toolScopeJson);
            if (!root.isArray()) {
                return ToolScope.empty();
            }
            Set<Long> toolIds = new HashSet<>();
            Set<String> toolCodes = new HashSet<>();
            for (JsonNode item : root) {
                JsonNode toolId = item.get("toolId");
                if (toolId != null && toolId.canConvertToLong()) {
                    toolIds.add(toolId.asLong());
                }
                JsonNode toolCode = item.get("toolCode");
                if (toolCode != null && toolCode.isTextual() && !toolCode.asText().isBlank()) {
                    toolCodes.add(toolCode.asText());
                }
            }
            return new ToolScope(toolIds, toolCodes);
        } catch (Exception ignored) {
            return ToolScope.empty();
        }
    }

    private void validateApprovedToolCall(ExecuteToolCallCommand command) {
        if (command.getApprovalRequestId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Approved tool approval is required");
        }
        ApprovalRequest approval = approvalRequestService.getApprovalRequest(command.getApprovalRequestId());
        if (!"approved".equals(approval.getApprovalStatus())
                || !"tool_call".equals(approval.getApprovalType())
                || !command.getTaskId().equals(approval.getTaskId())
                || !command.getRunId().equals(approval.getRunId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Approved tool approval is required");
        }
    }

    private EvaluatePolicyCommand toPolicyCommand(EvaluateToolCallCommand command) {
        EvaluatePolicyCommand policyCommand = new EvaluatePolicyCommand();
        policyCommand.setTargetType("tool");
        policyCommand.setTargetId(command.getToolId());
        policyCommand.setApplicantUserId(command.getApplicantUserId());
        policyCommand.setFallbackApproverUserId(command.getFallbackApproverUserId());
        policyCommand.setContextJson(command.getCallPayloadJson());
        return policyCommand;
    }

    private CreateApprovalRequestCommand toApprovalCommand(EvaluateToolCallCommand command,
                                                           ToolConfig tool,
                                                           PolicyDecisionResponse policy) {
        Long approverUserId = policy.getApproverUserIds().isEmpty()
                ? command.getFallbackApproverUserId()
                : policy.getApproverUserIds().get(0);
        if (approverUserId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Approval approver is required");
        }
        CreateApprovalRequestCommand approvalCommand = new CreateApprovalRequestCommand();
        approvalCommand.setTaskId(command.getTaskId());
        approvalCommand.setRunId(command.getRunId());
        approvalCommand.setApplicantUserId(command.getApplicantUserId());
        approvalCommand.setApproverUserId(approverUserId);
        approvalCommand.setApprovalType("tool_call");
        approvalCommand.setReason("Tool call requires approval: " + tool.getToolName());
        approvalCommand.setRequestPayloadJson(command.getCallPayloadJson() == null ? "{}" : command.getCallPayloadJson());
        return approvalCommand;
    }

    private String executeAllowedTool(ToolConfig tool, ExecuteToolCallCommand command) {
        if (Boolean.TRUE.equals(command.getDryRun())) {
            return "{\"dryRun\":true}";
        }
        return toolExecutorRegistry.execute(tool, command);
    }

    private ToolCallLog saveCallLog(ExecuteToolCallCommand command,
                                    ToolConfig tool,
                                    String status,
                                    String responseSummary,
                                    String errorMessage,
                                    int latencyMs,
                                    Long approvalRequestId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ToolCallLog log = new ToolCallLog();
        log.setTenantId(tenantId);
        log.setTaskId(command.getTaskId());
        log.setRunId(command.getRunId());
        log.setToolId(tool.getId());
        log.setRiskLevel(tool.getRiskLevel());
        log.setCallStatus(status);
        log.setRequestSummary(truncate(command.getCallPayloadJson()));
        log.setResponseSummary(truncate(responseSummary));
        log.setErrorMessage(truncate(errorMessage));
        log.setLatencyMs(latencyMs);
        log.setApprovalRequestId(approvalRequestId);
        log.setTraceId(UserContextHolder.get() == null ? null : UserContextHolder.get().getTraceId());
        toolCallLogService.save(log);
        return log;
    }

    private int elapsedMs(long start) {
        return Math.toIntExact(Math.max(0, System.currentTimeMillis() - start));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ToolScope(Set<Long> toolIds, Set<String> toolCodes) {
        static ToolScope empty() {
            return new ToolScope(Set.of(), Set.of());
        }

        boolean isEmpty() {
            return toolIds.isEmpty() && toolCodes.isEmpty();
        }
    }
}
