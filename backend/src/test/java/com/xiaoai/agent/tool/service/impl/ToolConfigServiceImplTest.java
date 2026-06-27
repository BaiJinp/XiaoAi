package com.xiaoai.agent.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.model.ApprovalRequestResponse;
import com.xiaoai.agent.approval.model.CreateApprovalRequestCommand;
import com.xiaoai.agent.approval.service.ApprovalRequestService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;
import com.xiaoai.agent.policy.service.PolicyRuleService;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.mapper.ToolConfigMapper;
import com.xiaoai.agent.tool.model.EvaluateToolCallCommand;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallDecisionResponse;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolCallLogService;
import com.xiaoai.agent.tool.entity.ToolCallLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolConfigServiceImplTest {

    private final ToolConfigMapper toolConfigMapper = mock(ToolConfigMapper.class);
    private final PolicyRuleService policyRuleService = mock(PolicyRuleService.class);
    private final ApprovalRequestService approvalRequestService = mock(ApprovalRequestService.class);
    private final ToolCallLogService toolCallLogService = mock(ToolCallLogService.class);
    private final AgentVersionMapper agentVersionMapper = mock(AgentVersionMapper.class);
    private final TaskEventRecordService taskEventRecordService = mock(TaskEventRecordService.class);
    private final ToolConfigServiceImpl toolConfigService = new ToolConfigServiceImpl(
            policyRuleService,
            approvalRequestService,
            toolCallLogService,
            com.xiaoai.agent.tool.executor.ToolExecutorRegistry.defaultRegistry(),
            agentVersionMapper,
            taskEventRecordService,
            new ObjectMapper()
    );

    ToolConfigServiceImplTest() {
        TestReflectionUtils.injectBaseMapper(toolConfigService, toolConfigMapper);
    }

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-1")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void evaluateToolCallShouldAllowWithoutApprovalRequest() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("allow")
                .approverUserIds(List.of())
                .reason("No active policy rule matched")
                .build());

        ToolCallDecisionResponse response = toolConfigService.evaluateToolCall(command());

        assertThat(response.getDecision()).isEqualTo("allow");
        assertThat(response.getRiskLevel()).isEqualTo("high");
        assertThat(response.getApprovalRequestId()).isNull();
        verify(approvalRequestService, never()).createApproval(any());
    }

    @Test
    void getToolConfigShouldReturnTenantScopedToolConfig() {
        ToolConfig tool = highRiskTool();
        tool.setTenantId(100L);
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);

        ToolConfig result = toolConfigService.getToolConfig(10L);

        assertThat(result.getToolName()).isEqualTo("创建项目任务");
    }

    @Test
    void getToolConfigShouldRejectMissingOrCrossTenantToolConfig() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> toolConfigService.getToolConfig(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tool config not found");
    }

    @Test
    void evaluateToolCallShouldRejectMissingTenantContext() {
        UserContextHolder.clear();

        assertThatThrownBy(() -> toolConfigService.evaluateToolCall(command()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing tenant context");

        verify(toolConfigMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void evaluateToolCallShouldCreateApprovalWhenPolicyRequiresApprove() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("approve")
                .approverUserIds(List.of(300L))
                .reason("高风险工具审批")
                .build());
        when(approvalRequestService.createApproval(any(CreateApprovalRequestCommand.class))).thenReturn(
                ApprovalRequestResponse.builder()
                        .approvalRequestId(88L)
                        .requestCode("APR-1")
                        .approvalStatus("pending")
                        .build()
        );

        ToolCallDecisionResponse response = toolConfigService.evaluateToolCall(command());

        ArgumentCaptor<CreateApprovalRequestCommand> captor = ArgumentCaptor.forClass(CreateApprovalRequestCommand.class);
        verify(approvalRequestService).createApproval(captor.capture());
        CreateApprovalRequestCommand approval = captor.getValue();
        assertThat(approval.getTaskId()).isEqualTo(1L);
        assertThat(approval.getRunId()).isEqualTo(99L);
        assertThat(approval.getApplicantUserId()).isEqualTo(200L);
        assertThat(approval.getApproverUserId()).isEqualTo(300L);
        assertThat(approval.getApprovalType()).isEqualTo("tool_call");
        assertThat(approval.getRequestPayloadJson()).isEqualTo("{\"amount\":100}");
        assertThat(response.getDecision()).isEqualTo("approve");
        assertThat(response.getApprovalRequestId()).isEqualTo(88L);
        assertThat(response.getApprovalStatus()).isEqualTo("pending");
    }

    @Test
    void executeToolCallShouldRunBuiltinEchoAndSaveSuccessLog() {
        ToolConfig tool = highRiskTool();
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("allow")
                .approverUserIds(List.of())
                .reason("No active policy rule matched")
                .build());
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(66L);
            return true;
        });

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(executeCommand());

        ArgumentCaptor<ToolCallLog> captor = ArgumentCaptor.forClass(ToolCallLog.class);
        verify(toolCallLogService).save(captor.capture());
        ToolCallLog log = captor.getValue();
        assertThat(log.getCallStatus()).isEqualTo("success");
        assertThat(log.getRequestSummary()).isEqualTo("{\"amount\":100}");
        assertThat(log.getResponseSummary()).isEqualTo("{\"amount\":100}");
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getToolCallLogId()).isEqualTo(66L);
        assertThat(response.getResultJson()).isEqualTo("{\"amount\":100}");
    }

    @Test
    void executeToolCallShouldAllowWhenAgentVersionToolScopeContainsTool() {
        ToolConfig tool = highRiskTool();
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":10,\"toolCode\":\"builtin.echo\"}]"));
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("allow")
                .approverUserIds(List.of())
                .reason("No active policy rule matched")
                .build());
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(71L);
            return true;
        });
        ExecuteToolCallCommand command = executeCommand();
        command.setAgentVersionId(13L);

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(command);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getToolCallLogId()).isEqualTo(71L);
        verify(policyRuleService).evaluate(any(EvaluatePolicyCommand.class));
    }

    @Test
    void executeToolCallShouldRejectWhenAgentVersionToolScopeDoesNotContainTool() {
        ToolConfig tool = highRiskTool();
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":99,\"toolCode\":\"other.tool\"}]"));
        ExecuteToolCallCommand command = executeCommand();
        command.setAgentVersionId(13L);

        assertThatThrownBy(() -> toolConfigService.executeToolCall(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tool call denied by agent version tool scope");

        ArgumentCaptor<RuntimeEvent> eventCaptor = ArgumentCaptor.forClass(RuntimeEvent.class);
        verify(taskEventRecordService).record(eventCaptor.capture());
        RuntimeEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("TOOL_DENIED");
        assertThat(event.getTaskId()).isEqualTo(1L);
        assertThat(event.getRunId()).isEqualTo(99L);
        assertThat(event.getPayloadJson()).contains("\"toolId\":10");
        assertThat(event.getPayloadJson()).contains("\"toolCode\":\"builtin.echo\"");
        assertThat(event.getPayloadJson()).contains("\"agentVersionId\":13");
        assertThat(event.getPayloadJson()).contains("\"reason\":\"tool_not_in_agent_version_scope\"");
        verify(policyRuleService, never()).evaluate(any(EvaluatePolicyCommand.class));
        verify(toolCallLogService, never()).save(any(ToolCallLog.class));
    }

    @Test
    void executeToolCallShouldBlockAndSaveLogWhenApprovalRequired() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("approve")
                .approverUserIds(List.of(300L))
                .reason("高风险工具审批")
                .build());
        when(approvalRequestService.createApproval(any(CreateApprovalRequestCommand.class))).thenReturn(
                ApprovalRequestResponse.builder()
                        .approvalRequestId(88L)
                        .requestCode("APR-1")
                        .approvalStatus("pending")
                        .build()
        );
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(67L);
            return true;
        });

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(executeCommand());

        ArgumentCaptor<ToolCallLog> captor = ArgumentCaptor.forClass(ToolCallLog.class);
        verify(toolCallLogService).save(captor.capture());
        ToolCallLog log = captor.getValue();
        assertThat(log.getCallStatus()).isEqualTo("blocked");
        assertThat(log.getApprovalRequestId()).isEqualTo(88L);
        assertThat(log.getErrorMessage()).isEqualTo("高风险工具审批");
        assertThat(response.getStatus()).isEqualTo("blocked");
        assertThat(response.getToolCallLogId()).isEqualTo(67L);
        assertThat(response.getApprovalRequestId()).isEqualTo(88L);
    }

    @Test
    void executeToolCallShouldBypassPolicyWhenApprovalAlreadyHandled() {
        ToolConfig tool = highRiskTool();
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(68L);
            return true;
        });
        ExecuteToolCallCommand command = executeCommand();
        command.setApprovalBypassed(true);
        command.setApprovalRequestId(88L);
        when(approvalRequestService.getApprovalRequest(88L)).thenReturn(approvedToolApproval(1L, 99L));

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(command);

        verify(policyRuleService, never()).evaluate(any(EvaluatePolicyCommand.class));
        verify(approvalRequestService, never()).createApproval(any());
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getToolCallLogId()).isEqualTo(68L);
        assertThat(response.getResultJson()).isEqualTo("{\"amount\":100}");
    }

    @Test
    void executeToolCallShouldRejectBypassWithoutApprovedToolApproval() {
        ToolConfig tool = highRiskTool();
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);
        ExecuteToolCallCommand command = executeCommand();
        command.setApprovalBypassed(true);
        command.setApprovalRequestId(88L);
        when(approvalRequestService.getApprovalRequest(88L)).thenReturn(pendingToolApproval(1L, 99L));

        assertThatThrownBy(() -> toolConfigService.executeToolCall(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approved tool approval is required");

        verify(policyRuleService, never()).evaluate(any(EvaluatePolicyCommand.class));
        verify(toolCallLogService, never()).save(any(ToolCallLog.class));
    }

    @Test
    void executeToolCallShouldRejectBypassWhenApprovalDoesNotMatchTaskRun() {
        ToolConfig tool = highRiskTool();
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);
        ExecuteToolCallCommand command = executeCommand();
        command.setApprovalBypassed(true);
        command.setApprovalRequestId(88L);
        when(approvalRequestService.getApprovalRequest(88L)).thenReturn(approvedToolApproval(2L, 99L));

        assertThatThrownBy(() -> toolConfigService.executeToolCall(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approved tool approval is required");

        verify(policyRuleService, never()).evaluate(any(EvaluatePolicyCommand.class));
        verify(toolCallLogService, never()).save(any(ToolCallLog.class));
    }

    @Test
    void executeToolCallShouldReturnDryRunResultAndSaveSuccessLog() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("allow")
                .approverUserIds(List.of())
                .reason("No active policy rule matched")
                .build());
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(69L);
            return true;
        });
        ExecuteToolCallCommand command = executeCommand();
        command.setDryRun(true);

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(command);

        ArgumentCaptor<ToolCallLog> captor = ArgumentCaptor.forClass(ToolCallLog.class);
        verify(toolCallLogService).save(captor.capture());
        ToolCallLog log = captor.getValue();
        assertThat(log.getCallStatus()).isEqualTo("success");
        assertThat(log.getResponseSummary()).isEqualTo("{\"dryRun\":true}");
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getToolCallLogId()).isEqualTo(69L);
        assertThat(response.getResultJson()).isEqualTo("{\"dryRun\":true}");
    }

    @Test
    void executeToolCallShouldSaveFailedLogWhenExecutorMissing() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("allow")
                .approverUserIds(List.of())
                .reason("No active policy rule matched")
                .build());
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(70L);
            return true;
        });

        ToolCallExecuteResponse response = toolConfigService.executeToolCall(executeCommand());

        ArgumentCaptor<ToolCallLog> captor = ArgumentCaptor.forClass(ToolCallLog.class);
        verify(toolCallLogService).save(captor.capture());
        ToolCallLog log = captor.getValue();
        assertThat(log.getCallStatus()).isEqualTo("failed");
        assertThat(log.getErrorMessage()).isEqualTo("Tool executor not implemented: null");
        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getToolCallLogId()).isEqualTo(70L);
        assertThat(response.getErrorMessage()).isEqualTo("Tool executor not implemented: null");
    }

    @Test
    void executeToolCallShouldRejectInactiveToolBeforePolicyEvaluation() {
        ToolConfig tool = highRiskTool();
        tool.setStatus("disabled");
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool);

        assertThatThrownBy(() -> toolConfigService.executeToolCall(executeCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tool config is not active");

        verify(policyRuleService, never()).evaluate(any(EvaluatePolicyCommand.class));
        verify(toolCallLogService, never()).save(any(ToolCallLog.class));
    }

    @Test
    void evaluateToolCallShouldUseFallbackApproverWhenPolicyHasNoApprovers() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("approve")
                .approverUserIds(List.of())
                .reason("高风险工具审批")
                .build());
        when(approvalRequestService.createApproval(any(CreateApprovalRequestCommand.class))).thenReturn(
                ApprovalRequestResponse.builder()
                        .approvalRequestId(89L)
                        .requestCode("APR-2")
                        .approvalStatus("pending")
                        .build()
        );

        ToolCallDecisionResponse response = toolConfigService.evaluateToolCall(command());

        ArgumentCaptor<CreateApprovalRequestCommand> captor = ArgumentCaptor.forClass(CreateApprovalRequestCommand.class);
        verify(approvalRequestService).createApproval(captor.capture());
        assertThat(captor.getValue().getApproverUserId()).isEqualTo(300L);
        assertThat(response.getDecision()).isEqualTo("approve");
        assertThat(response.getApprovalRequestId()).isEqualTo(89L);
    }

    @Test
    void evaluateToolCallShouldRejectApproveDecisionWithoutAnyApprover() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(highRiskTool());
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("approve")
                .approverUserIds(List.of())
                .reason("高风险工具审批")
                .build());
        EvaluateToolCallCommand command = command();
        command.setFallbackApproverUserId(null);

        assertThatThrownBy(() -> toolConfigService.evaluateToolCall(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approval approver is required");
    }

    private EvaluateToolCallCommand command() {
        EvaluateToolCallCommand command = new EvaluateToolCallCommand();
        command.setToolId(10L);
        command.setTaskId(1L);
        command.setRunId(99L);
        command.setApplicantUserId(200L);
        command.setFallbackApproverUserId(300L);
        command.setCallPayloadJson("{\"amount\":100}");
        return command;
    }

    private ExecuteToolCallCommand executeCommand() {
        ExecuteToolCallCommand command = new ExecuteToolCallCommand();
        command.setToolId(10L);
        command.setTaskId(1L);
        command.setRunId(99L);
        command.setApplicantUserId(200L);
        command.setFallbackApproverUserId(300L);
        command.setCallPayloadJson("{\"amount\":100}");
        command.setDryRun(false);
        return command;
    }

    private ToolConfig highRiskTool() {
        ToolConfig tool = new ToolConfig();
        tool.setId(10L);
        tool.setToolName("创建项目任务");
        tool.setRiskLevel("high");
        tool.setStatus("active");
        return tool;
    }

    private AgentVersion agentVersion(String toolScopeJson) {
        AgentVersion version = new AgentVersion();
        version.setId(13L);
        version.setTenantId(100L);
        version.setAgentId(12L);
        version.setVersionStatus("draft");
        version.setToolScopeJson(toolScopeJson);
        return version;
    }

    private ApprovalRequest approvedToolApproval(Long taskId, Long runId) {
        ApprovalRequest approval = pendingToolApproval(taskId, runId);
        approval.setApprovalStatus("approved");
        return approval;
    }

    private ApprovalRequest pendingToolApproval(Long taskId, Long runId) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setId(88L);
        approval.setTenantId(100L);
        approval.setTaskId(taskId);
        approval.setRunId(runId);
        approval.setApprovalType("tool_call");
        approval.setApprovalStatus("pending");
        return approval;
    }
}
