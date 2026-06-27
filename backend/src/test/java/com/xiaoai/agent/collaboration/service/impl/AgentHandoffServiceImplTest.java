package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.ArtifactType;
import com.xiaoai.agent.collaboration.mapper.AgentHandoffMapper;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.ArtifactTypeService;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHandoffServiceImplTest {

    private final AgentHandoffMapper mapper = mock(AgentHandoffMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollaborationSessionService sessionService = mock(CollaborationSessionService.class);
    private final AgentThreadService threadService = mock(AgentThreadService.class);
    private final TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
    private final ArtifactTypeService artifactTypeService = mock(ArtifactTypeService.class);
    private final AgentHandoffServiceImpl service = new AgentHandoffServiceImpl(sessionService, threadService, taskArtifactService, artifactTypeService);

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(service, mapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
        when(artifactTypeService.list(any(Wrapper.class))).thenReturn(java.util.List.of(new ArtifactType()));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createHandoffShouldPersistArtifactHandoffWhenArtifactTypeExists() throws Exception {
        TaskArtifact artifact = artifact("analysis_report", 101L);
        artifact.setRunId(301L);
        artifact.setArtifactName("Requirement Analysis");
        artifact.setMetadataJson("{\"artifactVersion\":2,\"producerAgentId\":501,\"stageCode\":\"requirement_analysis\"}");
        when(taskArtifactService.getArtifact(30L)).thenReturn(artifact);
        when(threadService.getThread(10L)).thenReturn(thread(10L, 1L, 101L, "Requirement analysis"));
        when(threadService.getThread(20L)).thenReturn(thread(20L, 1L, 202L, "Technical design"));
        when(mapper.insert(any(AgentHandoff.class))).thenAnswer(invocation -> {
            AgentHandoff handoff = invocation.getArgument(0);
            handoff.setId(99L);
            return 1;
        });
        CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
        command.setSessionId(1L);
        command.setFromThreadId(10L);
        command.setToThreadId(20L);
        command.setArtifactId(30L);
        command.setHandoffType("artifact");
        command.setMessageText("请基于交付物继续处理");

        AgentHandoff result = service.createHandoff(command);

        ArgumentCaptor<AgentHandoff> captor = ArgumentCaptor.forClass(AgentHandoff.class);
        verify(mapper).insert(captor.capture());
        AgentHandoff saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getSessionId()).isEqualTo(1L);
        assertThat(saved.getArtifactId()).isEqualTo(30L);
        assertThat(saved.getHandoffType()).isEqualTo("artifact");
        assertThat(saved.getStatus()).isEqualTo("pending");
        JsonNode metadata = objectMapper.readTree(saved.getMetadataJson());
        assertThat(metadata.path("artifactId").asLong()).isEqualTo(30L);
        assertThat(metadata.path("artifactRunId").asLong()).isEqualTo(301L);
        assertThat(metadata.path("fromThreadName").asText()).isEqualTo("Requirement analysis");
        assertThat(metadata.path("toThreadName").asText()).isEqualTo("Technical design");
        assertThat(metadata.path("artifactMetadata").path("artifactVersion").asInt()).isEqualTo(2);
        assertThat(metadata.path("artifactMetadata").path("producerAgentId").asLong()).isEqualTo(501L);
        assertThat(metadata.path("artifactMetadata").path("stageCode").asText()).isEqualTo("requirement_analysis");
        assertThat(result.getId()).isEqualTo(99L);
    }

    @Test
    void createHandoffShouldRejectMissingArtifactType() {
        when(taskArtifactService.getArtifact(30L)).thenReturn(artifact("missing_type"));
        when(artifactTypeService.list(any(Wrapper.class))).thenReturn(java.util.List.of());
        CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
        command.setSessionId(1L);
        command.setArtifactId(30L);
        command.setHandoffType("artifact");

        assertThatThrownBy(() -> service.createHandoff(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Artifact type not found");

        verify(mapper, never()).insert(any(AgentHandoff.class));
    }

    @Test
    void createHandoffShouldRejectThreadsFromDifferentSession() {
        when(taskArtifactService.getArtifact(30L)).thenReturn(artifact("analysis_report"));
        when(threadService.getThread(10L)).thenReturn(thread(10L, 1L));
        when(threadService.getThread(20L)).thenReturn(thread(20L, 2L));
        CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
        command.setSessionId(1L);
        command.setFromThreadId(10L);
        command.setToThreadId(20L);
        command.setArtifactId(30L);
        command.setHandoffType("artifact");

        assertThatThrownBy(() -> service.createHandoff(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent handoff threads must belong to same session");

        verify(mapper, never()).insert(any(AgentHandoff.class));
    }

    @Test
    void createHandoffShouldRejectArtifactFromDifferentFromThreadTask() {
        when(taskArtifactService.getArtifact(30L)).thenReturn(artifact("analysis_report", 101L));
        when(threadService.getThread(10L)).thenReturn(thread(10L, 1L, 202L));
        when(threadService.getThread(20L)).thenReturn(thread(20L, 1L));
        CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
        command.setSessionId(1L);
        command.setFromThreadId(10L);
        command.setToThreadId(20L);
        command.setArtifactId(30L);
        command.setHandoffType("artifact");

        assertThatThrownBy(() -> service.createHandoff(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent handoff artifact must belong to from thread task");

        verify(mapper, never()).insert(any(AgentHandoff.class));
    }

    @Test
    void createHandoffShouldRejectMessageOnlyHandoffWithoutArtifact() {
        CreateAgentHandoffCommand command = new CreateAgentHandoffCommand();
        command.setSessionId(1L);
        command.setHandoffType("message");
        command.setMessageText("仅口头说明");

        assertThatThrownBy(() -> service.createHandoff(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent handoff requires artifact");

        verify(mapper, never()).insert(any(AgentHandoff.class));
    }

    @Test
    void acceptAndRejectShouldUpdateTenantScopedHandoffStatus() {
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(1L);
        handoff.setTenantId(100L);
        handoff.setStatus("pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(handoff);
        List<String> updatedStatuses = new ArrayList<>();
        when(mapper.updateById(any(AgentHandoff.class))).thenAnswer(invocation -> {
            AgentHandoff updated = invocation.getArgument(0);
            updatedStatuses.add(updated.getStatus());
            return 1;
        });

        service.acceptHandoff(1L);
        service.rejectHandoff(1L);

        verify(mapper, org.mockito.Mockito.times(2)).updateById(any(AgentHandoff.class));
        assertThat(updatedStatuses).containsExactly("accepted", "rejected");
    }

    @Test
    void hasUnacceptedArtifactHandoffShouldReturnTrueOnlyForPendingArtifactHandoff() {
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(1L);
        handoff.setStatus("pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(handoff);

        assertThat(service.hasUnacceptedArtifactHandoff(1L, 20L, 30L)).isTrue();

        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(service.hasUnacceptedArtifactHandoff(1L, 20L, 30L)).isFalse();
        assertThat(service.hasUnacceptedArtifactHandoff(null, 20L, 30L)).isFalse();
    }

    @Test
    void getHandoffShouldReturnTenantScopedHandoff() {
        AgentHandoff handoff = new AgentHandoff();
        handoff.setId(1L);
        handoff.setTenantId(100L);
        handoff.setHandoffType("artifact");
        handoff.setStatus("pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(handoff);

        AgentHandoff result = service.getHandoff(1L);

        assertThat(result.getHandoffType()).isEqualTo("artifact");
        assertThat(result.getStatus()).isEqualTo("pending");
    }

    @Test
    void getHandoffShouldRejectMissingOrCrossTenantHandoff() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getHandoff(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent handoff not found");
    }

    private AgentThread thread(Long id, Long sessionId) {
        return thread(id, sessionId, null);
    }

    private AgentThread thread(Long id, Long sessionId, Long taskId) {
        return thread(id, sessionId, taskId, null);
    }

    private AgentThread thread(Long id, Long sessionId, Long taskId, String threadName) {
        AgentThread thread = new AgentThread();
        thread.setId(id);
        thread.setTenantId(100L);
        thread.setSessionId(sessionId);
        thread.setTaskId(taskId);
        thread.setAgentId(401L + id);
        thread.setAgentVersionId(801L + id);
        thread.setThreadName(threadName);
        return thread;
    }

    private TaskArtifact artifact(String artifactType) {
        return artifact(artifactType, null);
    }

    private TaskArtifact artifact(String artifactType, Long taskId) {
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(30L);
        artifact.setTenantId(100L);
        artifact.setArtifactType(artifactType);
        artifact.setTaskId(taskId);
        return artifact;
    }
}
