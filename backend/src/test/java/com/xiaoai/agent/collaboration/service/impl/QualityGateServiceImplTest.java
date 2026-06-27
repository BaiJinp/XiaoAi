package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.mapper.AgentThreadMapper;
import com.xiaoai.agent.collaboration.mapper.QualityGateMapper;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.model.UpdateQualityGateCommand;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.service.TaskArtifactService;
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

class QualityGateServiceImplTest {

    private final QualityGateMapper mapper = mock(QualityGateMapper.class);
    private final QualityGateServiceImpl service = new QualityGateServiceImpl();

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
    void createGateShouldPersistPendingGateUnderCurrentTenant() {
        when(mapper.insert(any(QualityGate.class))).thenAnswer(invocation -> {
            QualityGate gate = invocation.getArgument(0);
            gate.setId(10L);
            return 1;
        });
        CreateQualityGateCommand command = new CreateQualityGateCommand();
        command.setSessionId(1L);
        command.setGateCode("generic_gate");
        command.setGateName("Generic Gate");
        command.setGateType("manual_confirmation");
        command.setRequired(true);
        command.setRuleJson("{\"requiredArtifacts\":[\"analysis_report\"]}");

        QualityGate result = service.createGate(command);

        ArgumentCaptor<QualityGate> captor = ArgumentCaptor.forClass(QualityGate.class);
        verify(mapper).insert(captor.capture());
        QualityGate saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getSessionId()).isEqualTo(1L);
        assertThat(saved.getGateCode()).isEqualTo("generic_gate");
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getRequired()).isTrue();
        assertThat(saved.getRuleJson()).contains("analysis_report");
        assertThat(saved.getResultJson()).isEqualTo("{}");
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void passGateShouldMarkGatePassedWithResultJson() {
        QualityGate gate = gate(1L, true, "pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(gate);
        UpdateQualityGateCommand command = new UpdateQualityGateCommand();
        command.setResultJson("{\"confirmedBy\":200}");

        QualityGate result = service.passGate(1L, 1L, command);

        ArgumentCaptor<QualityGate> captor = ArgumentCaptor.forClass(QualityGate.class);
        verify(mapper).updateById(captor.capture());
        QualityGate updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("passed");
        assertThat(updated.getResultJson()).contains("confirmedBy");
        assertThat(updated.getFailReason()).isNull();
        assertThat(updated.getPassedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("passed");
    }

    @Test
    void passGateShouldAttachQualitySnapshotForGateArtifacts() {
        QualityGateMapper gateMapper = mock(QualityGateMapper.class);
        AgentThreadMapper threadMapper = mock(AgentThreadMapper.class);
        TaskArtifactService artifactService = mock(TaskArtifactService.class);
        QualityGateServiceImpl qualityService = new QualityGateServiceImpl();
        TestReflectionUtils.injectBaseMapper(qualityService, gateMapper);
        qualityService.setQualitySnapshotDependencies(threadMapper, artifactService);
        QualityGate gate = gate(7L, true, "pending");
        gate.setGateCode("implementation_done");
        when(gateMapper.selectOne(any(Wrapper.class))).thenReturn(gate);
        when(threadMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                thread(7L, "backend_implementation", 701L),
                thread(7L, "frontend_implementation", 702L),
                thread(7L, "technical_design", 700L)
        ));
        when(artifactService.getArtifact(701L)).thenReturn(artifact(701L, "backend_implementation"));
        when(artifactService.getArtifact(702L)).thenReturn(artifact(702L, "frontend_implementation"));
        UpdateQualityGateCommand command = new UpdateQualityGateCommand();
        command.setResultJson("{\"confirmedBy\":200}");

        QualityGate result = qualityService.passGate(7L, 1L, command);

        ArgumentCaptor<QualityGate> captor = ArgumentCaptor.forClass(QualityGate.class);
        verify(gateMapper).updateById(captor.capture());
        String resultJson = captor.getValue().getResultJson();
        assertThat(resultJson).contains("\"confirmedBy\":200");
        assertThat(resultJson).contains("\"quality\"");
        assertThat(resultJson).contains("\"status\":\"passed\"");
        assertThat(resultJson).contains("\"artifactCount\":2");
        assertThat(resultJson).contains("\"artifactId\":701");
        assertThat(resultJson).contains("\"artifactId\":702");
        assertThat(resultJson).doesNotContain("\"artifactId\":700");
        assertThat(result.getResultJson()).isEqualTo(resultJson);
    }

    @Test
    void passGateShouldWarnWhenGateArtifactContentIsMissing() {
        QualityGateMapper gateMapper = mock(QualityGateMapper.class);
        AgentThreadMapper threadMapper = mock(AgentThreadMapper.class);
        TaskArtifactService artifactService = mock(TaskArtifactService.class);
        QualityGateServiceImpl qualityService = new QualityGateServiceImpl();
        TestReflectionUtils.injectBaseMapper(qualityService, gateMapper);
        qualityService.setQualitySnapshotDependencies(threadMapper, artifactService);
        QualityGate gate = gate(7L, true, "pending");
        gate.setGateCode("tests_passed");
        when(gateMapper.selectOne(any(Wrapper.class))).thenReturn(gate);
        when(threadMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                thread(7L, "testing", 704L)
        ));
        TaskArtifact artifact = artifact(704L, "testing");
        artifact.setContentText(null);
        artifact.setStorageUrl(null);
        when(artifactService.getArtifact(704L)).thenReturn(artifact);
        UpdateQualityGateCommand command = new UpdateQualityGateCommand();

        qualityService.passGate(7L, 1L, command);

        ArgumentCaptor<QualityGate> captor = ArgumentCaptor.forClass(QualityGate.class);
        verify(gateMapper).updateById(captor.capture());
        assertThat(captor.getValue().getResultJson()).contains("\"status\":\"warning\"");
        assertThat(captor.getValue().getResultJson()).contains("\"artifactCount\":1");
        assertThat(captor.getValue().getResultJson()).contains("artifact #704 has no content or storage url");
    }

    @Test
    void passGateShouldUseDeliveryConfirmedForFinalDeliveryArtifact() {
        QualityGateMapper gateMapper = mock(QualityGateMapper.class);
        AgentThreadMapper threadMapper = mock(AgentThreadMapper.class);
        TaskArtifactService artifactService = mock(TaskArtifactService.class);
        QualityGateServiceImpl qualityService = new QualityGateServiceImpl();
        TestReflectionUtils.injectBaseMapper(qualityService, gateMapper);
        qualityService.setQualitySnapshotDependencies(threadMapper, artifactService);
        QualityGate gate = gate(7L, true, "pending");
        gate.setGateCode("delivery_confirmed");
        when(gateMapper.selectOne(any(Wrapper.class))).thenReturn(gate);
        when(threadMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                thread(7L, "review_and_delivery", 706L),
                thread(7L, "testing", 705L)
        ));
        when(artifactService.getArtifact(706L)).thenReturn(artifact(706L, "review_and_delivery"));

        qualityService.passGate(7L, 1L, new UpdateQualityGateCommand());

        ArgumentCaptor<QualityGate> captor = ArgumentCaptor.forClass(QualityGate.class);
        verify(gateMapper).updateById(captor.capture());
        assertThat(captor.getValue().getResultJson()).contains("\"artifactCount\":1");
        assertThat(captor.getValue().getResultJson()).contains("\"artifactId\":706");
        assertThat(captor.getValue().getResultJson()).doesNotContain("\"artifactId\":705");
    }

    @Test
    void passGateShouldRejectGateFromDifferentSession() {
        QualityGate gate = gate(2L, true, "pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(gate);

        assertThatThrownBy(() -> service.passGate(1L, 1L, new UpdateQualityGateCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quality gate must belong to session");

        verify(mapper, never()).updateById(any(QualityGate.class));
    }

    @Test
    void passGateShouldRejectKnownAgileGateBeforeAllTargetStagesAreComplete() {
        QualityGateMapper gateMapper = mock(QualityGateMapper.class);
        AgentThreadMapper threadMapper = mock(AgentThreadMapper.class);
        TaskArtifactService artifactService = mock(TaskArtifactService.class);
        QualityGateServiceImpl qualityService = new QualityGateServiceImpl();
        TestReflectionUtils.injectBaseMapper(qualityService, gateMapper);
        qualityService.setQualitySnapshotDependencies(threadMapper, artifactService);
        QualityGate gate = gate(7L, true, "pending");
        gate.setGateCode("implementation_done");
        when(gateMapper.selectOne(any(Wrapper.class))).thenReturn(gate);
        when(threadMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                thread(7L, "backend_implementation", 701L)
        ));

        assertThatThrownBy(() -> qualityService.passGate(7L, 1L, new UpdateQualityGateCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quality gate target stage is not created: frontend_implementation");

        verify(gateMapper, never()).updateById(any(QualityGate.class));
    }

    @Test
    void failGateShouldMarkGateFailedWithReason() {
        QualityGate gate = gate(1L, true, "pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(gate);
        UpdateQualityGateCommand command = new UpdateQualityGateCommand();
        command.setResultJson("{\"missing\":[\"test_report\"]}");
        command.setFailReason("test report missing");

        QualityGate result = service.failGate(1L, 1L, command);

        ArgumentCaptor<QualityGate> captor = ArgumentCaptor.forClass(QualityGate.class);
        verify(mapper).updateById(captor.capture());
        QualityGate updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("failed");
        assertThat(updated.getResultJson()).contains("test_report");
        assertThat(updated.getFailReason()).isEqualTo("test report missing");
        assertThat(updated.getPassedAt()).isNull();
        assertThat(result.getStatus()).isEqualTo("failed");
    }

    @Test
    void hasBlockingFailedGateShouldReturnTrueWhenRequiredGateFailed() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(gate(1L, true, "failed")));

        boolean blocked = service.hasBlockingFailedGate(1L);

        assertThat(blocked).isTrue();
    }

    @Test
    void hasBlockingFailedGateShouldReturnFalseWhenOnlyOptionalGateFailed() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(gate(1L, false, "failed")));

        boolean blocked = service.hasBlockingFailedGate(1L);

        assertThat(blocked).isFalse();
    }

    @Test
    void createGateShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        CreateQualityGateCommand command = new CreateQualityGateCommand();
        command.setSessionId(1L);
        command.setGateCode("generic_gate");
        command.setGateName("Generic Gate");
        command.setGateType("manual_confirmation");

        assertThatThrownBy(() -> service.createGate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(mapper, never()).insert(any(QualityGate.class));
    }

    @Test
    void getGateShouldReturnTenantScopedGate() {
        QualityGate gate = new QualityGate();
        gate.setId(1L);
        gate.setTenantId(100L);
        gate.setGateCode("generic_gate");
        gate.setStatus("pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(gate);

        QualityGate result = service.getGate(1L);

        assertThat(result.getGateCode()).isEqualTo("generic_gate");
        assertThat(result.getStatus()).isEqualTo("pending");
    }

    @Test
    void getGateShouldRejectMissingOrCrossTenantGate() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getGate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quality gate not found");
    }

    private AgentThread thread(Long sessionId, String stageCode, Long outputArtifactId) {
        AgentThread thread = new AgentThread();
        thread.setId(outputArtifactId);
        thread.setTenantId(100L);
        thread.setSessionId(sessionId);
        thread.setStatus("completed");
        thread.setOutputArtifactId(outputArtifactId);
        thread.setContextJson("{\"stageCode\":\"" + stageCode + "\"}");
        return thread;
    }

    private TaskArtifact artifact(Long artifactId, String stageCode) {
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(artifactId);
        artifact.setTenantId(100L);
        artifact.setTaskId(80L);
        artifact.setArtifactType("implementation_summary");
        artifact.setArtifactName("Artifact " + artifactId);
        artifact.setContentText("done");
        artifact.setMetadataJson("{\"artifactVersion\":1,"
                + "\"producerAgentId\":10,"
                + "\"producerAgentVersionId\":11,"
                + "\"collaborationSessionId\":7,"
                + "\"stageCode\":\"" + stageCode + "\"}");
        return artifact;
    }

    private QualityGate gate(Long sessionId, boolean required, String status) {
        QualityGate gate = new QualityGate();
        gate.setId(1L);
        gate.setTenantId(100L);
        gate.setSessionId(sessionId);
        gate.setGateCode("generic_gate");
        gate.setGateName("Generic Gate");
        gate.setGateType("manual_confirmation");
        gate.setRequired(required);
        gate.setStatus(status);
        gate.setRuleJson("{}");
        gate.setResultJson("{}");
        return gate;
    }
}
