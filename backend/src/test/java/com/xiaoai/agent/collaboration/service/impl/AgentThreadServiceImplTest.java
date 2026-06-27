package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.mapper.AgentHandoffMapper;
import com.xiaoai.agent.collaboration.mapper.AgentThreadMapper;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.service.AgentRoleService;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskService;
import com.xiaoai.agent.test.TestReflectionUtils;
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

class AgentThreadServiceImplTest {

    private final AgentThreadMapper mapper = mock(AgentThreadMapper.class);
    private final CollaborationSessionService sessionService = mock(CollaborationSessionService.class);
    private final AgentRoleService agentRoleService = mock(AgentRoleService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
    private final AgentHandoffMapper handoffMapper = mock(AgentHandoffMapper.class);
    private final AgentThreadServiceImpl service = new AgentThreadServiceImpl(sessionService, agentRoleService, taskService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(service, mapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createThreadShouldCreateTenantScopedThreadUnderSession() {
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentRole role = new AgentRole();
        role.setId(20L);
        role.setTenantId(100L);
        when(agentRoleService.getRole(20L)).thenReturn(role);
        when(mapper.insert(any(AgentThread.class))).thenAnswer(invocation -> {
            AgentThread thread = invocation.getArgument(0);
            thread.setId(30L);
            return 1;
        });
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(10L);
        command.setAgentId(40L);
        command.setAgentVersionId(41L);
        command.setRoleId(20L);
        command.setThreadName("analysis-thread");
        command.setInputArtifactId(50L);

        AgentThreadResponse response = service.createThread(command);

        ArgumentCaptor<AgentThread> captor = ArgumentCaptor.forClass(AgentThread.class);
        verify(mapper).insert(captor.capture());
        AgentThread saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getSessionId()).isEqualTo(10L);
        assertThat(saved.getThreadCode()).startsWith("TH");
        assertThat(saved.getThreadName()).isEqualTo("analysis-thread");
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getInputArtifactId()).isEqualTo(50L);
        assertThat(readJson(saved.getContextJson()).path("threadName").asText()).isEqualTo("analysis-thread");
        assertThat(response.getThreadId()).isEqualTo(30L);
        assertThat(response.getStatus()).isEqualTo("pending");
    }

    @Test
    void createThreadShouldRejectMissingSession() {
        when(sessionService.getSession(10L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Collaboration session not found"));
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(10L);
        command.setAgentId(40L);

        assertThatThrownBy(() -> service.createThread(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Collaboration session not found");

        verify(mapper, never()).insert(any(AgentThread.class));
    }

    @Test
    void createThreadShouldRejectRoleFromAnotherTenant() {
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        when(agentRoleService.getRole(20L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Agent role not found"));
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(10L);
        command.setAgentId(40L);
        command.setRoleId(20L);

        assertThatThrownBy(() -> service.createThread(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent role not found");

        verify(mapper, never()).insert(any(AgentThread.class));
    }

    @Test
    void createThreadShouldCreateTaskWhenRequested() {
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        when(taskService.createTask(any(CreateTaskCommand.class))).thenReturn(TaskCreateResponse.builder()
                .taskId(99L)
                .taskCode("TASK-1")
                .status("pending")
                .build());
        when(mapper.insert(any(AgentThread.class))).thenAnswer(invocation -> {
            AgentThread thread = invocation.getArgument(0);
            thread.setId(30L);
            return 1;
        });
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(10L);
        command.setAgentId(40L);
        command.setAgentVersionId(41L);
        command.setThreadName("execution-thread");
        command.setCreateTask(true);
        command.setInputArtifactId(50L);
        command.setInputArtifactIds(List.of(50L, 51L));
        command.setOutputArtifactTypes(List.of("implementation_summary"));
        command.setInputArtifactVersion(2L);
        command.setStageCode("implementation");
        command.setInputText("execute input");
        command.setToolCallsJson("""
                [{"toolId":22,"toolCode":"controlled.http.project-query","callPayloadJson":{"query":"risk"}}]
                """);

        service.createThread(command);

        ArgumentCaptor<CreateTaskCommand> taskCaptor = ArgumentCaptor.forClass(CreateTaskCommand.class);
        verify(taskService).createTask(taskCaptor.capture());
        CreateTaskCommand taskCommand = taskCaptor.getValue();
        assertThat(taskCommand.getAgentId()).isEqualTo(40L);
        assertThat(taskCommand.getAgentVersionId()).isEqualTo(41L);
        assertThat(taskCommand.getUserId()).isEqualTo(200L);
        assertThat(taskCommand.getChannelType()).isEqualTo("collaboration");
        assertThat(taskCommand.getTitle()).isEqualTo("execution-thread");

        JsonNode input = readJson(taskCommand.getInputText());
        assertThat(input.path("assistantTaskType").asText()).isEqualTo("agile_implementation");
        assertThat(input.path("prompt").asText()).isEqualTo("execute input");
        assertThat(input.path("inputText").asText()).isEqualTo("execute input");
        assertThat(input.path("collaborationContext").path("sessionId").asLong()).isEqualTo(10L);
        assertThat(input.path("collaborationContext").path("agentId").asLong()).isEqualTo(40L);
        assertThat(input.path("collaborationContext").path("agentVersionId").asLong()).isEqualTo(41L);
        assertThat(input.path("collaborationContext").path("inputArtifactId").asLong()).isEqualTo(50L);
        assertThat(input.path("collaborationContext").path("inputArtifactIds")).extracting(JsonNode::asLong)
                .containsExactly(50L, 51L);
        assertThat(input.path("outputArtifactTypes")).extracting(JsonNode::asText)
                .containsExactly("implementation_summary");
        assertThat(input.path("collaborationContext").path("outputArtifactTypes")).extracting(JsonNode::asText)
                .containsExactly("implementation_summary");
        assertThat(input.path("collaborationContext").path("inputArtifactVersion").asLong()).isEqualTo(2L);
        assertThat(input.path("collaborationContext").path("stageCode").asText()).isEqualTo("implementation");
        assertThat(input.path("collaborationContext").path("threadName").asText()).isEqualTo("execution-thread");
        assertThat(input.path("toolCalls").isArray()).isTrue();
        assertThat(input.path("toolCalls").get(0).path("toolId").asLong()).isEqualTo(22L);
        assertThat(input.path("toolCalls").get(0).path("toolCode").asText()).isEqualTo("controlled.http.project-query");

        ArgumentCaptor<AgentThread> threadCaptor = ArgumentCaptor.forClass(AgentThread.class);
        verify(mapper).insert(threadCaptor.capture());
        JsonNode context = readJson(threadCaptor.getValue().getContextJson());
        assertThat(context.path("inputArtifactIds")).extracting(JsonNode::asLong)
                .containsExactly(50L, 51L);
        assertThat(context.path("stageCode").asText()).isEqualTo("implementation");
        assertThat(context.path("threadName").asText()).isEqualTo("execution-thread");
        assertThat(input.path("toolCalls").get(0).path("callPayloadJson").path("query").asText()).isEqualTo("risk");
    }

    @Test
    void startThreadTaskShouldStartBoundTaskAndSyncThreadStatus() {
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        when(taskService.startTask(any(Long.class), any(StartTaskCommand.class))).thenReturn(TaskRunResponse.builder()
                .taskId(99L)
                .runId(77L)
                .runCode("RUN-77")
                .status("running")
                .runtimeType("java-in-process")
                .build());
        Task task = new Task();
        task.setId(99L);
        task.setStatus("completed");
        when(taskService.getTask(99L)).thenReturn(task);

        TaskRunResponse response = service.startThreadTask(10L, 30L, new StartTaskCommand());

        assertThat(response.getRunId()).isEqualTo(77L);
        verify(taskService).startTask(any(Long.class), any(StartTaskCommand.class));
        verify(mapper, times(2)).updateById(thread);
        assertThat(thread.getStatus()).isEqualTo("completed");
    }

    @Test
    void startThreadTaskShouldSyncLatestTaskArtifactAsThreadOutput() {
        AgentThreadServiceImpl serviceWithArtifacts = new AgentThreadServiceImpl(sessionService, agentRoleService, taskService, taskArtifactService);
        TestReflectionUtils.injectBaseMapper(serviceWithArtifacts, mapper);
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        when(taskService.startTask(any(Long.class), any(StartTaskCommand.class))).thenReturn(TaskRunResponse.builder()
                .taskId(99L)
                .runId(77L)
                .status("running")
                .runtimeType("java-in-process")
                .build());
        Task task = new Task();
        task.setId(99L);
        task.setStatus("completed");
        when(taskService.getTask(99L)).thenReturn(task);
        TaskArtifact oldArtifact = new TaskArtifact();
        oldArtifact.setId(100L);
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(123L);
        when(taskArtifactService.listByTaskId(99L)).thenReturn(java.util.List.of(oldArtifact, artifact));

        serviceWithArtifacts.startThreadTask(10L, 30L, new StartTaskCommand());

        assertThat(thread.getOutputArtifactId()).isEqualTo(123L);
    }

    @Test
    void startThreadTaskShouldRejectMismatchedInputArtifactVersion() {
        AgentThreadServiceImpl serviceWithArtifacts = new AgentThreadServiceImpl(sessionService, agentRoleService, taskService, taskArtifactService);
        TestReflectionUtils.injectBaseMapper(serviceWithArtifacts, mapper);
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        thread.setInputArtifactId(50L);
        thread.setContextJson("{\"inputArtifactVersion\":2}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(50L);
        artifact.setMetadataJson("{\"artifactVersion\":1}");
        when(taskArtifactService.getArtifact(50L)).thenReturn(artifact);

        assertThatThrownBy(() -> serviceWithArtifacts.startThreadTask(10L, 30L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread input artifact version mismatch");

        verify(taskService, never()).startTask(any(Long.class), any(StartTaskCommand.class));
    }

    @Test
    void startThreadTaskShouldRejectUnacceptedInputArtifactHandoff() {
        AgentThreadServiceImpl serviceWithHandoffCheck = new AgentThreadServiceImpl(
                sessionService,
                agentRoleService,
                taskService,
                taskArtifactService,
                handoffMapper
        );
        TestReflectionUtils.injectBaseMapper(serviceWithHandoffCheck, mapper);
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        thread.setInputArtifactId(50L);
        thread.setContextJson("{\"requireAcceptedInputHandoff\":true}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(70L);
        handoff.setStatus("pending");
        when(handoffMapper.selectOne(any(Wrapper.class))).thenReturn(handoff);

        assertThatThrownBy(() -> serviceWithHandoffCheck.startThreadTask(10L, 30L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread input artifact handoff must be accepted before start");

        verify(taskService, never()).startTask(any(Long.class), any(StartTaskCommand.class));
    }

    @Test
    void startThreadTaskShouldRejectStrictInputArtifactWithoutAcceptedHandoff() {
        AgentThreadServiceImpl serviceWithHandoffCheck = new AgentThreadServiceImpl(
                sessionService,
                agentRoleService,
                taskService,
                taskArtifactService,
                handoffMapper
        );
        TestReflectionUtils.injectBaseMapper(serviceWithHandoffCheck, mapper);
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        thread.setInputArtifactId(50L);
        thread.setContextJson("{\"requireAcceptedInputHandoff\":true}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        when(handoffMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> serviceWithHandoffCheck.startThreadTask(10L, 30L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread input artifact handoff must be accepted before start");

        verify(taskService, never()).startTask(any(Long.class), any(StartTaskCommand.class));
    }

    @Test
    void startThreadTaskShouldRejectStrictMultiInputArtifactsUntilEveryHandoffIsAccepted() {
        AgentThreadServiceImpl serviceWithHandoffCheck = new AgentThreadServiceImpl(
                sessionService,
                agentRoleService,
                taskService,
                taskArtifactService,
                handoffMapper
        );
        TestReflectionUtils.injectBaseMapper(serviceWithHandoffCheck, mapper);
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        thread.setInputArtifactId(50L);
        thread.setContextJson("{\"requireAcceptedInputHandoff\":true,\"inputArtifactIds\":[50,51]}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        AgentHandoff accepted = new AgentHandoff();
        accepted.setId(70L);
        accepted.setStatus("accepted");
        when(handoffMapper.selectOne(any(Wrapper.class))).thenReturn(null, accepted, null);

        assertThatThrownBy(() -> serviceWithHandoffCheck.startThreadTask(10L, 30L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread input artifact handoff must be accepted before start");

        verify(taskService, never()).startTask(any(Long.class), any(StartTaskCommand.class));
        verify(handoffMapper, times(4)).selectOne(any(Wrapper.class));
    }

    @Test
    void startThreadTaskShouldAllowStrictMultiInputArtifactsWhenEveryHandoffIsAccepted() {
        AgentThreadServiceImpl serviceWithHandoffCheck = new AgentThreadServiceImpl(
                sessionService,
                agentRoleService,
                taskService,
                taskArtifactService,
                handoffMapper
        );
        TestReflectionUtils.injectBaseMapper(serviceWithHandoffCheck, mapper);
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, 99L);
        thread.setInputArtifactId(50L);
        thread.setContextJson("{\"requireAcceptedInputHandoff\":true,\"inputArtifactIds\":[50,51]}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        AgentHandoff accepted = new AgentHandoff();
        accepted.setId(70L);
        accepted.setStatus("accepted");
        when(handoffMapper.selectOne(any(Wrapper.class))).thenReturn(null, accepted, null, accepted);
        when(taskService.startTask(any(Long.class), any(StartTaskCommand.class))).thenReturn(TaskRunResponse.builder()
                .taskId(99L)
                .runId(77L)
                .status("running")
                .runtimeType("java-in-process")
                .build());
        Task task = new Task();
        task.setId(99L);
        task.setStatus("completed");
        when(taskService.getTask(99L)).thenReturn(task);

        TaskRunResponse response = serviceWithHandoffCheck.startThreadTask(10L, 30L, new StartTaskCommand());

        assertThat(response.getRunId()).isEqualTo(77L);
        verify(taskService).startTask(any(Long.class), any(StartTaskCommand.class));
        verify(handoffMapper, times(4)).selectOne(any(Wrapper.class));
    }

    @Test
    void syncThreadByTaskIdShouldUpdateThreadStatusAndOutputArtifactAfterAsyncTaskCompletion() {
        AgentThreadServiceImpl serviceWithArtifacts = new AgentThreadServiceImpl(sessionService, agentRoleService, taskService, taskArtifactService);
        TestReflectionUtils.injectBaseMapper(serviceWithArtifacts, mapper);
        AgentThread thread = thread(30L, 10L, 99L);
        thread.setStatus("suspended");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);
        Task task = new Task();
        task.setId(99L);
        task.setStatus("completed");
        when(taskService.getTask(99L)).thenReturn(task);
        TaskArtifact oldArtifact = new TaskArtifact();
        oldArtifact.setId(100L);
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(123L);
        when(taskArtifactService.listByTaskId(99L)).thenReturn(java.util.List.of(artifact, oldArtifact));

        serviceWithArtifacts.syncThreadByTaskId(99L);

        verify(mapper).updateById(thread);
        assertThat(thread.getStatus()).isEqualTo("completed");
        assertThat(thread.getOutputArtifactId()).isEqualTo(123L);
    }

    @Test
    void syncThreadByTaskIdShouldIgnoreTasksWithoutCollaborationThread() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.syncThreadByTaskId(99L);

        verify(taskService, never()).getTask(any(Long.class));
        verify(mapper, never()).updateById(any(AgentThread.class));
    }

    @Test
    void startThreadTaskShouldRejectThreadWithoutBoundTask() {
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread thread = thread(30L, 10L, null);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);

        assertThatThrownBy(() -> service.startThreadTask(10L, 30L, new StartTaskCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread has no bound task");

        verify(taskService, never()).startTask(any(Long.class), any(StartTaskCommand.class));
    }

    @Test
    void createThreadShouldRejectChildDepthOverOne() {
        when(sessionService.getSession(10L)).thenReturn(session(10L));
        AgentThread parent = thread(5L, 10L, null);
        parent.setParentThreadId(4L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(parent);
        CreateAgentThreadCommand command = new CreateAgentThreadCommand();
        command.setSessionId(10L);
        command.setParentThreadId(5L);
        command.setAgentId(40L);

        assertThatThrownBy(() -> service.createThread(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread depth exceeds MVP limit 1");

        verify(mapper, never()).insert(any(AgentThread.class));
    }

    @Test
    void getThreadShouldReturnTenantScopedThread() {
        AgentThread thread = thread(1L, 10L, null);
        thread.setThreadCode("thread_001");
        thread.setThreadName("execution-thread");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(thread);

        AgentThread result = service.getThread(1L);

        assertThat(result.getThreadCode()).isEqualTo("thread_001");
        assertThat(result.getThreadName()).isEqualTo("execution-thread");
    }

    @Test
    void getThreadShouldRejectMissingOrCrossTenantThread() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getThread(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent thread not found");
    }

    private CollaborationSession session(Long sessionId) {
        CollaborationSession session = new CollaborationSession();
        session.setId(sessionId);
        session.setTenantId(100L);
        session.setStatus("planning");
        return session;
    }

    private AgentThread thread(Long threadId, Long sessionId, Long taskId) {
        AgentThread thread = new AgentThread();
        thread.setId(threadId);
        thread.setTenantId(100L);
        thread.setSessionId(sessionId);
        thread.setTaskId(taskId);
        return thread;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
