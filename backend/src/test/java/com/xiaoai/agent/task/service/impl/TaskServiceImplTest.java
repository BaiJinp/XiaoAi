package com.xiaoai.agent.task.service.impl;

import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.runtime.gateway.RuntimeGateway;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.event.TaskStatusChangedEvent;
import com.xiaoai.agent.task.mapper.TaskMapper;
import com.xiaoai.agent.task.model.CancelTaskCommand;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.ResumeTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.SuspendForApprovalCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.task.service.TaskEventService;
import com.xiaoai.agent.task.service.TaskRunService;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceImplTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final TaskRunService taskRunService = mock(TaskRunService.class);
    private final TaskEventService taskEventService = mock(TaskEventService.class);
    private final TaskEventRecordService taskEventRecordService = mock(TaskEventRecordService.class);
    private final TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
    private final RuntimeGateway runtimeGateway = mock(RuntimeGateway.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final TaskServiceImpl taskService = new TaskServiceImpl(
            taskRunService,
            taskEventService,
            taskEventRecordService,
            taskArtifactService,
            runtimeGateway,
            agentVersionService
    );

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(taskService, taskMapper);
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
    void createTaskShouldPersistPendingTask() {
        when(taskMapper.insert(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(1L);
            return 1;
        });
        CreateTaskCommand command = new CreateTaskCommand();
        command.setAgentId(10L);
        command.setAgentVersionId(11L);
        command.setUserId(200L);
        command.setInputText("生成项目周报");

        TaskCreateResponse response = taskService.createTask(command);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(captor.capture());
        Task saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getChannelType()).isEqualTo("web_chat");
        assertThat(response.getTaskId()).isEqualTo(1L);
    }

    @Test
    void getTaskShouldReturnTenantScopedTask() {
        Task task = runningCandidateTask();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        Task result = taskService.getTask(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTenantId()).isEqualTo(100L);
    }

    @Test
    void createTaskShouldRejectMissingTenantContext() {
        UserContextHolder.clear();
        CreateTaskCommand command = new CreateTaskCommand();
        command.setAgentId(10L);
        command.setUserId(200L);
        command.setInputText("weekly report");

        assertThatThrownBy(() -> taskService.createTask(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing tenant context");

        verify(taskMapper, never()).insert(any(Task.class));
    }

    @Test
    void createTaskShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        CreateTaskCommand command = new CreateTaskCommand();
        command.setAgentId(10L);
        command.setUserId(200L);
        command.setInputText("weekly report");

        assertThatThrownBy(() -> taskService.createTask(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(taskMapper, never()).insert(any(Task.class));
    }

    @Test
    void startTaskShouldCreateRunAndRecordStartedEvent() {
        Task task = runningCandidateTask();
        AgentVersion version = new AgentVersion();
        version.setId(11L);
        version.setRuntimeSnapshotJson("{\"memoryPolicy\":{\"enabled\":true,\"maxItems\":3}}");
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(agentVersionService.getVersion(11L)).thenReturn(version);
        when(taskRunService.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(99L);
            return true;
        });
        when(taskRunService.getById(99L)).thenReturn(runningRun());
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(runtimeGateway.startRun(any(RunStartCommand.class))).thenReturn(RunStartResult.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(99L)
                .runtimeType("java-in-process")
                .accepted(true)
                .build());
        when(runtimeGateway.listEvents(99L)).thenReturn(List.of(RuntimeEvent.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .taskId(1L)
                .runId(99L)
                .eventType("ASSISTANT_ARTIFACT")
                .eventSummary("Project assistant artifact generated")
                .payloadJson("{\"artifactType\":\"markdown\",\"artifactName\":\"项目周报\",\"contentText\":\"# 项目周报\",\"metadata\":{\"source\":\"runtime\"}}")
                .build(),
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .userId(200L)
                        .agentId(10L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("ASSISTANT_TASK_COMPLETED")
                        .eventSummary("Project assistant task completed")
                        .payloadJson("{\"assistantTaskType\":\"weekly_report\"}")
                        .build()));
        when(taskRunService.updateById(any(TaskRun.class))).thenReturn(true);
        when(taskArtifactService.save(any(TaskArtifact.class))).thenReturn(true);

        TaskRunResponse response = taskService.startTask(1L, new StartTaskCommand());

        verify(taskRunService).save(any(TaskRun.class));
        ArgumentCaptor<RunStartCommand> runStartCaptor = ArgumentCaptor.forClass(RunStartCommand.class);
        verify(runtimeGateway).startRun(runStartCaptor.capture());
        assertThat(runStartCaptor.getValue().getRuntimeSnapshotJson()).isEqualTo("{\"memoryPolicy\":{\"enabled\":true,\"maxItems\":3}}");
        verify(runtimeGateway).listEvents(99L);
        verify(taskEventRecordService, times(4)).record(any());
        ArgumentCaptor<TaskArtifact> artifactCaptor = ArgumentCaptor.forClass(TaskArtifact.class);
        verify(taskArtifactService).save(artifactCaptor.capture());
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("markdown");
        assertThat(artifactCaptor.getValue().getArtifactName()).isEqualTo("项目周报");
        assertThat(artifactCaptor.getValue().getContentText()).isEqualTo("# 项目周报");
        assertThat(task.getStatus()).isEqualTo("completed");
        assertThat(task.getResultSummary()).isEqualTo("# 项目周报");
        assertThat(task.getCurrentRunId()).isEqualTo(99L);
        assertThat(response.getRunId()).isEqualTo(99L);
        assertThat(response.getRuntimeType()).isEqualTo("java-in-process");
    }

    @Test
    void startTaskShouldNotCompleteWhenRuntimeHasBlockedEvent() {
        Task task = runningCandidateTask();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskRunService.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(99L);
            return true;
        });
        when(taskRunService.getById(99L)).thenReturn(runningRun());
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(runtimeGateway.startRun(any(RunStartCommand.class))).thenReturn(RunStartResult.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(99L)
                .runtimeType("java-in-process")
                .accepted(true)
                .build());
        when(runtimeGateway.listEvents(99L)).thenReturn(List.of(
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("MODEL_RESULT")
                        .payloadJson("{\"content\":\"周报\"}")
                        .build(),
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("TOOL_BLOCKED")
                        .payloadJson("{\"approvalRequestId\":88}")
                        .build()
        ));

        taskService.startTask(1L, new StartTaskCommand());

        assertThat(task.getStatus()).isEqualTo("suspended");
        verify(taskArtifactService, never()).save(any(TaskArtifact.class));
    }

    @Test
    void startTaskShouldMergeCollaborationContextIntoArtifactMetadata() {
        Task task = runningCandidateTask();
        task.setChannelType("collaboration");
        task.setInputText("""
                {"inputText":"write tech plan","collaborationContext":{"sessionId":7,"threadName":"dev-thread","stageCode":"dev","agentVersionId":11,"inputArtifactId":30}}
                """);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskRunService.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(99L);
            return true;
        });
        when(taskRunService.getById(99L)).thenReturn(runningRun());
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(runtimeGateway.startRun(any(RunStartCommand.class))).thenReturn(RunStartResult.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(99L)
                .runtimeType("java-in-process")
                .accepted(true)
                .build());
        when(runtimeGateway.listEvents(99L)).thenReturn(List.of(
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .userId(200L)
                        .agentId(10L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("ASSISTANT_ARTIFACT")
                        .eventSummary("Artifact generated")
                        .payloadJson("{\"artifactType\":\"tech_design\",\"artifactName\":\"Tech Design\",\"contentText\":\"done\",\"metadata\":{\"source\":\"runtime\",\"lowConfidence\":false}}")
                        .build(),
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .userId(200L)
                        .agentId(10L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("ASSISTANT_TASK_COMPLETED")
                        .eventSummary("Task completed")
                        .payloadJson("{}")
                        .build()));
        when(taskRunService.updateById(any(TaskRun.class))).thenReturn(true);
        when(taskArtifactService.save(any(TaskArtifact.class))).thenReturn(true);

        taskService.startTask(1L, new StartTaskCommand());

        ArgumentCaptor<TaskArtifact> artifactCaptor = ArgumentCaptor.forClass(TaskArtifact.class);
        verify(taskArtifactService).save(artifactCaptor.capture());
        String metadataJson = artifactCaptor.getValue().getMetadataJson();
        assertThat(metadataJson).contains("\"source\":\"runtime\"");
        assertThat(metadataJson).contains("\"lowConfidence\":false");
        assertThat(metadataJson).contains("\"collaborationContext\"");
        assertThat(metadataJson).contains("\"artifactVersion\":1");
        assertThat(metadataJson).contains("\"producerAgentId\":10");
        assertThat(metadataJson).contains("\"producerAgentVersionId\":11");
        assertThat(metadataJson).contains("\"collaborationSessionId\":7");
        assertThat(metadataJson).contains("\"sessionId\":7");
        assertThat(metadataJson).contains("\"threadName\":\"dev-thread\"");
        assertThat(metadataJson).contains("\"stageCode\":\"dev\"");
        assertThat(metadataJson).contains("\"agentVersionId\":11");
        assertThat(metadataJson).contains("\"inputArtifactId\":30");
    }

    @Test
    void cancelTaskShouldUpdateTaskRunAndRecordCancelledEvent() {
        Task task = runningTask();
        TaskRun run = runningRun();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskRunService.getById(99L)).thenReturn(run);
        CancelTaskCommand command = new CancelTaskCommand();
        command.setOperatorUserId(200L);
        command.setReason("用户取消");

        taskService.cancelTask(1L, command);

        assertThat(task.getStatus()).isEqualTo("cancelled");
        assertThat(run.getStatus()).isEqualTo("cancelled");
        verify(runtimeGateway).cancelRun(any());
        verify(taskEventRecordService).record(any());
    }

    @Test
    void suspendAndResumeShouldSwitchRunStatusAndRecordEvents() {
        Task task = runningTask();
        TaskRun run = runningRun();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskRunService.getById(99L)).thenReturn(run);

        SuspendForApprovalCommand suspend = new SuspendForApprovalCommand();
        suspend.setApprovalRequestId(88L);
        suspend.setReason("需要审批");
        taskService.suspendForApproval(1L, suspend);

        assertThat(task.getStatus()).isEqualTo("suspended");
        assertThat(run.getStatus()).isEqualTo("suspended");
        verify(taskEventRecordService, times(2)).record(any());

        ResumeTaskCommand resume = new ResumeTaskCommand();
        resume.setApprovalRequestId(88L);
        resume.setResumePayloadJson("{}");
        taskService.resumeTask(1L, resume);

        assertThat(task.getStatus()).isEqualTo("running");
        assertThat(run.getStatus()).isEqualTo("running");
        verify(runtimeGateway).submitApprovalResult(any());
        verify(runtimeGateway).resumeRun(any());
        verify(taskEventRecordService, times(3)).record(any());
    }

    @Test
    void resumeTaskShouldCompleteWhenRuntimeReturnsCompletedEvents() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        TaskServiceImpl publishingTaskService = new TaskServiceImpl(
                taskRunService,
                taskEventService,
                taskEventRecordService,
                taskArtifactService,
                runtimeGateway,
                agentVersionService,
                eventPublisher
        );
        TestReflectionUtils.injectBaseMapper(publishingTaskService, taskMapper);
        Task task = runningTask();
        task.setStatus("suspended");
        TaskRun run = runningRun();
        run.setStatus("suspended");
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskRunService.getById(99L)).thenReturn(run);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(taskRunService.updateById(any(TaskRun.class))).thenReturn(true);
        when(taskArtifactService.save(any(TaskArtifact.class))).thenReturn(true);
        when(runtimeGateway.listEvents(99L))
                .thenReturn(List.of(RuntimeEvent.builder()
                        .tenantId(100L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("TOOL_BLOCKED")
                        .build()))
                .thenReturn(List.of(
                        RuntimeEvent.builder()
                                .tenantId(100L)
                                .taskId(1L)
                                .runId(99L)
                                .eventType("TOOL_BLOCKED")
                                .build(),
                        RuntimeEvent.builder()
                                .tenantId(100L)
                                .taskId(1L)
                                .runId(99L)
                                .eventType("TOOL_RESULT")
                                .payloadJson("{\"result\":{\"amount\":100}}")
                                .build(),
                        RuntimeEvent.builder()
                                .tenantId(100L)
                                .taskId(1L)
                                .runId(99L)
                                .eventType("ASSISTANT_ARTIFACT")
                                .payloadJson("{\"artifactType\":\"action_items\",\"artifactName\":\"会议行动项\",\"contentText\":\"[{\\\"title\\\":\\\"确认会议结论\\\"}]\",\"metadata\":{\"source\":\"runtime\"}}")
                                .build(),
                        RuntimeEvent.builder()
                                .tenantId(100L)
                                .taskId(1L)
                                .runId(99L)
                                .eventType("ASSISTANT_TASK_COMPLETED")
                                .payloadJson("{\"approvalRequestId\":88}")
                                .build()
                ));
        ResumeTaskCommand resume = new ResumeTaskCommand();
        resume.setApprovalRequestId(88L);
        resume.setResumePayloadJson("{\"approvalRequestId\":88}");

        publishingTaskService.resumeTask(1L, resume);

        assertThat(task.getStatus()).isEqualTo("completed");
        assertThat(run.getStatus()).isEqualTo("completed");
        verify(runtimeGateway).resumeRun(any());
        ArgumentCaptor<TaskArtifact> artifactCaptor = ArgumentCaptor.forClass(TaskArtifact.class);
        verify(taskArtifactService).save(artifactCaptor.capture());
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("action_items");
        assertThat(artifactCaptor.getValue().getArtifactName()).isEqualTo("会议行动项");
        verify(taskEventRecordService, times(5)).record(any());
        ArgumentCaptor<TaskStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TaskStatusChangedEvent::getStatus)
                .containsExactly("running", "completed");
        assertThat(eventCaptor.getAllValues()).extracting(TaskStatusChangedEvent::getSource)
                .containsExactly("resume", "runtime_completed");
        assertThat(eventCaptor.getAllValues().get(1).getTaskId()).isEqualTo(1L);
        assertThat(eventCaptor.getAllValues().get(1).getRunId()).isEqualTo(99L);
    }

    @Test
    void startTaskShouldPublishCompletedStatusAfterArtifactSaved() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        TaskServiceImpl publishingTaskService = new TaskServiceImpl(
                taskRunService,
                taskEventService,
                taskEventRecordService,
                taskArtifactService,
                runtimeGateway,
                agentVersionService,
                eventPublisher
        );
        TestReflectionUtils.injectBaseMapper(publishingTaskService, taskMapper);
        Task task = runningCandidateTask();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(taskRunService.save(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId(99L);
            return true;
        });
        when(runtimeGateway.startRun(any(RunStartCommand.class))).thenReturn(RunStartResult.builder()
                .tenantId(100L)
                .taskId(1L)
                .runId(99L)
                .runtimeType("java-in-process")
                .accepted(true)
                .build());
        when(runtimeGateway.listEvents(99L)).thenReturn(List.of(
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("ASSISTANT_ARTIFACT")
                        .payloadJson("{\"artifactType\":\"markdown\",\"artifactName\":\"项目周报\",\"contentText\":\"done\",\"metadata\":{\"source\":\"runtime\"}}")
                        .build(),
                RuntimeEvent.builder()
                        .tenantId(100L)
                        .taskId(1L)
                        .runId(99L)
                        .eventType("ASSISTANT_TASK_COMPLETED")
                        .payloadJson("{}")
                        .build()
        ));

        publishingTaskService.startTask(1L, new StartTaskCommand());

        ArgumentCaptor<TaskStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TaskStatusChangedEvent::getStatus)
                .containsExactly("running", "completed");
        assertThat(eventCaptor.getAllValues().get(1).getTaskId()).isEqualTo(1L);
        assertThat(eventCaptor.getAllValues().get(1).getRunId()).isEqualTo(99L);
        assertThat(eventCaptor.getAllValues().get(1).getSource()).isEqualTo("runtime_completed");
        verify(taskArtifactService).save(any(TaskArtifact.class));
    }

    @Test
    void listTaskEventsShouldReturnEventsOrderedBySequenceId() {
        TaskEvent event = new TaskEvent();
        event.setEventType("RUN_STARTED");
        when(taskEventService.list(any(Wrapper.class))).thenReturn(List.of(event));

        List<TaskEvent> events = taskService.listTaskEvents(1L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("RUN_STARTED");
    }

    @Test
    void listTaskEventsAfterShouldReturnEventsAfterLastEventId() {
        TaskEvent event = new TaskEvent();
        event.setId(11L);
        event.setEventType("RUN_COMPLETED");
        when(taskEventService.list(any(Wrapper.class))).thenReturn(List.of(event));

        List<TaskEvent> events = taskService.listTaskEventsAfter(1L, 10L);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getSequence()).isEqualTo(11L);
    }

    @Test
    void startTaskShouldRejectMissingOrCrossTenantTask() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> taskService.startTask(1L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task not found");

        verify(taskRunService, never()).save(any(TaskRun.class));
        verify(runtimeGateway, never()).startRun(any(RunStartCommand.class));
    }

    @Test
    void getTaskShouldRejectMissingOrCrossTenantTask() {
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> taskService.getTask(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task not found");
    }

    @Test
    void startTaskShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());

        assertThatThrownBy(() -> taskService.startTask(1L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(taskRunService, never()).save(any(TaskRun.class));
        verify(runtimeGateway, never()).startRun(any(RunStartCommand.class));
    }

    private Task runningCandidateTask() {
        Task task = new Task();
        task.setId(1L);
        task.setTenantId(100L);
        task.setAgentId(10L);
        task.setAgentVersionId(11L);
        task.setUserId(200L);
        task.setChannelType("web_chat");
        task.setInputText("生成项目周报");
        task.setStatus("pending");
        return task;
    }

    private Task runningTask() {
        Task task = runningCandidateTask();
        task.setCurrentRunId(99L);
        task.setStatus("running");
        return task;
    }

    private TaskRun runningRun() {
        TaskRun run = new TaskRun();
        run.setId(99L);
        run.setTenantId(100L);
        run.setTaskId(1L);
        run.setStatus("running");
        run.setTraceId("trace-1");
        return run;
    }
}
