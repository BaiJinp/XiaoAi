package com.xiaoai.agent.task.integration;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.approval.entity.ApprovalRecord;
import com.xiaoai.agent.approval.entity.ApprovalRequest;
import com.xiaoai.agent.approval.event.ApprovalHandledEvent;
import com.xiaoai.agent.approval.mapper.ApprovalRequestMapper;
import com.xiaoai.agent.approval.model.HandleApprovalCommand;
import com.xiaoai.agent.approval.service.ApprovalRecordService;
import com.xiaoai.agent.approval.service.impl.ApprovalRequestServiceImpl;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;
import com.xiaoai.agent.policy.model.EvaluatePolicyCommand;
import com.xiaoai.agent.policy.model.PolicyDecisionResponse;
import com.xiaoai.agent.policy.service.PolicyRuleService;
import com.xiaoai.agent.runtime.gateway.JavaInProcessRuntimeGateway;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.listener.ApprovalHandledListener;
import com.xiaoai.agent.task.mapper.TaskMapper;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.task.service.TaskEventService;
import com.xiaoai.agent.task.service.TaskRunService;
import com.xiaoai.agent.task.service.impl.TaskServiceImpl;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolCallLog;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.mapper.ToolConfigMapper;
import com.xiaoai.agent.tool.service.ToolCallLogService;
import com.xiaoai.agent.tool.service.impl.ToolConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class ApprovalResumeFlowTest {

    private final AtomicLong approvalId = new AtomicLong(88L);
    private final List<RuntimeEvent> recordedEvents = new ArrayList<>();
    private final List<TaskArtifact> artifacts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-flow")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void approvedToolApprovalShouldResumeRuntimeAndCompleteTask() {
        Task task = task();
        TaskRun run = new TaskRun();
        ApprovalRequest[] savedApproval = new ApprovalRequest[1];

        TaskMapper taskMapper = mock(TaskMapper.class);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);

        TaskRunService taskRunService = mock(TaskRunService.class);
        when(taskRunService.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun savedRun = invocation.getArgument(0);
            savedRun.setId(99L);
            copyRun(savedRun, run);
            return true;
        });
        when(taskRunService.getById(99L)).thenReturn(run);
        when(taskRunService.updateById(any(TaskRun.class))).thenAnswer(invocation -> {
            copyRun(invocation.getArgument(0), run);
            return true;
        });

        TaskEventRecordService taskEventRecordService = mock(TaskEventRecordService.class);
        doAnswer(invocation -> {
            RuntimeEvent event = invocation.getArgument(0);
            recordedEvents.add(event);
            return null;
        }).when(taskEventRecordService).record(any(RuntimeEvent.class));
        TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
        when(taskArtifactService.save(any(TaskArtifact.class))).thenAnswer(invocation -> {
            artifacts.add(invocation.getArgument(0));
            return true;
        });

        ApprovalRecordService approvalRecordService = mock(ApprovalRecordService.class);
        ApprovalRequestMapper approvalRequestMapper = mock(ApprovalRequestMapper.class);
        when(approvalRequestMapper.insert(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest request = invocation.getArgument(0);
            request.setId(approvalId.get());
            savedApproval[0] = request;
            return 1;
        });
        when(approvalRequestMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> savedApproval[0]);
        when(approvalRequestMapper.updateById(any(ApprovalRequest.class))).thenReturn(1);

        PolicyRuleService policyRuleService = mock(PolicyRuleService.class);
        when(policyRuleService.evaluate(any(EvaluatePolicyCommand.class))).thenReturn(PolicyDecisionResponse.builder()
                .effect("approve")
                .approverUserIds(List.of(300L))
                .reason("高风险工具审批")
                .build());

        ToolCallLogService toolCallLogService = mock(ToolCallLogService.class);
        when(toolCallLogService.save(any(ToolCallLog.class))).thenAnswer(invocation -> {
            ToolCallLog log = invocation.getArgument(0);
            log.setId(log.getApprovalRequestId() == null ? 67L : 66L);
            return true;
        });

        ToolConfigMapper toolConfigMapper = mock(ToolConfigMapper.class);
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(echoTool());
        AgentVersionMapper agentVersionMapper = mock(AgentVersionMapper.class);
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion());
        RuntimeCheckpoint[] savedCheckpoint = new RuntimeCheckpoint[1];
        RuntimeCheckpointService runtimeCheckpointService = mock(RuntimeCheckpointService.class);
        when(runtimeCheckpointService.save(any(RuntimeCheckpoint.class))).thenAnswer(invocation -> {
            RuntimeCheckpoint checkpoint = invocation.getArgument(0);
            checkpoint.setId(77L);
            savedCheckpoint[0] = checkpoint;
            return true;
        });
        when(runtimeCheckpointService.getOne(any())).thenAnswer(invocation -> savedCheckpoint[0]);
        when(runtimeCheckpointService.updateById(any(RuntimeCheckpoint.class))).thenAnswer(invocation -> {
            savedCheckpoint[0] = invocation.getArgument(0);
            return true;
        });

        ApprovalRequestServiceImpl[] approvalRequestServiceRef = new ApprovalRequestServiceImpl[1];
        JavaInProcessRuntimeGateway runtimeGateway;
        ToolConfigServiceImpl toolConfigService;
        TaskServiceImpl taskService;

        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof ApprovalHandledEvent) {
                ApprovalHandledEvent approvalEvent = (ApprovalHandledEvent) event;
                ApprovalHandledListener listener = new ApprovalHandledListener(taskServiceRef[0], taskRunService, taskEventRecordService);
                listener.onApprovalHandled(approvalEvent);
            }
        };
        ApprovalRequestServiceImpl approvalRequestService = new ApprovalRequestServiceImpl(approvalRecordService, eventPublisher);
        approvalRequestServiceRef[0] = approvalRequestService;
        TestReflectionUtils.injectBaseMapper(approvalRequestService, approvalRequestMapper);

        toolConfigService = new ToolConfigServiceImpl(
                policyRuleService,
                approvalRequestServiceRef[0],
                toolCallLogService,
                com.xiaoai.agent.tool.executor.ToolExecutorRegistry.defaultRegistry(),
                agentVersionMapper,
                taskEventRecordService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
        TestReflectionUtils.injectBaseMapper(toolConfigService, toolConfigMapper);

        runtimeGateway = new JavaInProcessRuntimeGateway(
                toolConfigService,
                mock(ModelGateway.class),
                mock(KnowledgeDocumentService.class),
                runtimeCheckpointService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        taskService = new TaskServiceImpl(
                taskRunService,
                mock(TaskEventService.class),
                taskEventRecordService,
                taskArtifactService,
                runtimeGateway
        );
        taskServiceRef[0] = taskService;
        TestReflectionUtils.injectBaseMapper(taskService, taskMapper);

        taskService.startTask(1L, startCommand());

        assertThat(task.getStatus()).isEqualTo("suspended");
        assertThat(savedApproval[0]).isNotNull();
        assertThat(savedApproval[0].getApprovalStatus()).isEqualTo("pending");
        assertThat(savedCheckpoint[0]).isNotNull();
        assertThat(savedCheckpoint[0].getCheckpointStatus()).isEqualTo("suspended");
        assertThat(recordedEvents).extracting(RuntimeEvent::getEventType)
                .contains("TOOL_BLOCKED", "ASSISTANT_TASK_SUSPENDED");

        HandleApprovalCommand approve = new HandleApprovalCommand();
        approve.setOperatorUserId(300L);
        approve.setAction("approve");
        approve.setCommentText("同意");
        approvalRequestService.handleApproval(88L, approve);

        assertThat(savedApproval[0].getApprovalStatus()).isEqualTo("approved");
        assertThat(savedCheckpoint[0].getCheckpointStatus()).isEqualTo("completed");
        assertThat(task.getStatus()).isEqualTo("completed");
        assertThat(run.getStatus()).isEqualTo("completed");
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).getArtifactType()).isEqualTo("weekly_report");
        assertThat(artifacts.get(0).getContentText()).contains("项目周报");
        assertThat(recordedEvents).extracting(RuntimeEvent::getEventType)
                .contains("APPROVAL_APPROVED", "RUN_RESUMED", "TOOL_RESULT", "ASSISTANT_ARTIFACT", "ASSISTANT_TASK_COMPLETED", "RUN_COMPLETED");
    }

    private final TaskServiceImpl[] taskServiceRef = new TaskServiceImpl[1];

    private StartTaskCommand startCommand() {
        StartTaskCommand command = new StartTaskCommand();
        command.setRuntimeType("java-in-process");
        return command;
    }

    private Task task() {
        Task task = new Task();
        task.setId(1L);
        task.setTenantId(100L);
        task.setAgentId(10L);
        task.setAgentVersionId(11L);
        task.setUserId(200L);
        task.setChannelType("web_chat");
        task.setInputText("{\"assistantTaskType\":\"weekly_report\",\"toolId\":10,\"callPayloadJson\":{\"amount\":100}}");
        task.setStatus("pending");
        return task;
    }

    private ToolConfig echoTool() {
        ToolConfig tool = new ToolConfig();
        tool.setId(10L);
        tool.setToolName("创建项目任务");
        tool.setToolType("internal");
        tool.setToolCode("builtin.echo");
        tool.setRiskLevel("high");
        tool.setStatus("active");
        return tool;
    }

    private AgentVersion agentVersion() {
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setTenantId(100L);
        version.setAgentId(10L);
        version.setVersionStatus("draft");
        version.setToolScopeJson("[{\"toolId\":10,\"toolCode\":\"builtin.echo\"}]");
        return version;
    }

    private void copyRun(TaskRun source, TaskRun target) {
        target.setId(source.getId());
        target.setTenantId(source.getTenantId());
        target.setTaskId(source.getTaskId());
        target.setAgentId(source.getAgentId());
        target.setAgentVersionId(source.getAgentVersionId());
        target.setRuntimeType(source.getRuntimeType());
        target.setStatus(source.getStatus());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setTraceId(source.getTraceId());
        target.setSnapshotJson(source.getSnapshotJson());
        target.setUsageJson(source.getUsageJson());
        target.setSuspendReason(source.getSuspendReason());
        target.setFailReason(source.getFailReason());
    }
}
