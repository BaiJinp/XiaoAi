package com.xiaoai.agent.collaboration.strategy.impl;

import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.model.CreateQualityGateCommand;
import com.xiaoai.agent.collaboration.service.AgentHandoffService;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.collaboration.service.QualityGateService;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.model.StartTaskCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrchestratedTeamStrategyTest {

    private final CollaborationPlanService planService = mock(CollaborationPlanService.class);
    private final AgentThreadService threadService = mock(AgentThreadService.class);
    private final AgentHandoffService handoffService = mock(AgentHandoffService.class);
    private final QualityGateService qualityGateService = mock(QualityGateService.class);
    private final OrchestratedTeamStrategy strategy = new OrchestratedTeamStrategy(planService, threadService, handoffService, qualityGateService);

    private void noExistingStartArtifacts() {
        when(threadService.listThreads(1L)).thenReturn(List.of());
        when(qualityGateService.listGates(1L)).thenReturn(List.of());
    }

    @Test
    void startShouldCreateFirstThreadAndRequiredGatesFromValidatedPlan() {
        noExistingStartArtifacts();
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "goal": "完成一次通用协作交付",
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "agentVersionId": 40,
                      "roleId": 50,
                      "inputArtifactId": 60,
                      "createTask": true,
                      "inputText": "please clarify requirements",
                      "toolCalls": [
                        {
                          "toolId": 22,
                          "toolCode": "controlled.http.project-query",
                          "callPayloadJson": {"query": "requirements"}
                        }
                      ],
                      "requiresGate": "intake_confirmed",
                      "gateName": "Intake Confirmed",
                      "gateType": "manual_confirmation"
                    },
                    {
                      "stageCode": "delivery_review",
                      "agentId": 31,
                      "requiresGate": "delivery_confirmed"
                    }
                  ]
                }
                """));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH001")
                .taskId(101L)
                .status("pending")
                .build());

        CollaborationStrategyResult result = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .build());

        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(threadService).createThread(threadCaptor.capture());
        CreateAgentThreadCommand threadCommand = threadCaptor.getValue();
        assertThat(threadCommand.getSessionId()).isEqualTo(1L);
        assertThat(threadCommand.getAgentId()).isEqualTo(30L);
        assertThat(threadCommand.getAgentVersionId()).isEqualTo(40L);
        assertThat(threadCommand.getRoleId()).isEqualTo(50L);
        assertThat(threadCommand.getThreadName()).isEqualTo("Intake Review");
        assertThat(threadCommand.getStageCode()).isEqualTo("intake_review");
        assertThat(threadCommand.getInputArtifactId()).isEqualTo(60L);
        assertThat(threadCommand.isCreateTask()).isTrue();
        assertThat(threadCommand.getInputText()).isEqualTo("please clarify requirements");
        assertThat(threadCommand.getToolCallsJson()).contains("\"toolId\":22", "controlled.http.project-query");

        ArgumentCaptor<CreateQualityGateCommand> gateCaptor = ArgumentCaptor.forClass(CreateQualityGateCommand.class);
        verify(qualityGateService, org.mockito.Mockito.times(2)).createGate(gateCaptor.capture());
        assertThat(gateCaptor.getAllValues()).extracting(CreateQualityGateCommand::getGateCode)
                .containsExactly("intake_confirmed", "delivery_confirmed");
        assertThat(result.getStatus()).isEqualTo("started");
        assertThat(result.getCreatedThreadCount()).isEqualTo(1);
        assertThat(result.getCreatedGateCount()).isEqualTo(2);
        assertThat(result.getCurrentStageCode()).isEqualTo("intake_review");
        verify(threadService, never()).startThreadTask(any(), any(), any(StartTaskCommand.class));
    }

    @Test
    void startShouldAutoStartFirstStageTaskOnlyWhenExplicitlyConfigured() {
        noExistingStartArtifacts();
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "please clarify requirements"
                    }
                  ]
                }
                """));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH001")
                .taskId(101L)
                .status("pending")
                .build());

        strategy.start(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .build());

        verify(threadService).startThreadTask(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(100L), any(StartTaskCommand.class));
    }

    @Test
    void startShouldNotDuplicateExistingFirstThreadOrGates() {
        AgentThread existingThread = new AgentThread();
        existingThread.setThreadName("Intake Review");
        com.xiaoai.agent.collaboration.entity.QualityGate existingGate = new com.xiaoai.agent.collaboration.entity.QualityGate();
        existingGate.setGateCode("intake_confirmed");
        when(threadService.listThreads(1L)).thenReturn(List.of(existingThread));
        when(qualityGateService.listGates(1L)).thenReturn(List.of(existingGate));
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "createTask": true,
                      "autoStartTask": true,
                      "requiresGate": "intake_confirmed"
                    }
                  ]
                }
                """));

        CollaborationStrategyResult result = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .build());

        verify(threadService, never()).createThread(any(CreateAgentThreadCommand.class));
        verify(threadService, never()).startThreadTask(any(), any(), any(StartTaskCommand.class));
        verify(qualityGateService, never()).createGate(any(CreateQualityGateCommand.class));
        assertThat(result.getCreatedThreadCount()).isZero();
        assertThat(result.getCreatedGateCount()).isZero();
        assertThat(result.getCurrentStageCode()).isEqualTo("intake_review");
    }

    @Test
    void startShouldRejectPlanThatHasNotPassedValidation() {
        when(planService.getPlan(10L)).thenReturn(plan("failed", "{\"goal\":\"x\",\"stages\":[]}"));

        assertThatThrownBy(() -> strategy.start(CollaborationStrategyContext.builder()
                        .sessionId(1L)
                        .planId(10L)
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Collaboration plan must be validated before strategy start");

        verify(threadService, never()).createThread(any(CreateAgentThreadCommand.class));
        verify(qualityGateService, never()).createGate(any(CreateQualityGateCommand.class));
    }

    @Test
    void continueAfterGateShouldBlockWhenRequiredGateFailed() {
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(true);

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .build());

        assertThat(result.getStatus()).isEqualTo("blocked");
        assertThat(result.getMessage()).isEqualTo("Required quality gate failed");
    }

    @Test
    void continueAfterGateShouldCreateNextStageThreadWhenGatePassed() {
        AgentThread current = new AgentThread();
        current.setId(99L);
        current.setThreadName("Intake Review");
        current.setOutputArtifactId(88L);
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "requiresGate": "intake_confirmed"
                    },
                    {
                      "stageCode": "delivery_review",
                      "stageName": "Delivery Review",
                      "agentId": 31,
                      "agentVersionId": 41,
                      "inputArtifactVersion": 2,
                      "createTask": true,
                      "inputText": "review delivery"
                    }
                  ]
                }
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(current));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH100")
                .taskId(101L)
                .status("pending")
                .build());
        when(handoffService.listHandoffs(1L)).thenReturn(List.of());

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("intake_confirmed")
                .build());

        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(threadService).createThread(threadCaptor.capture());
        CreateAgentThreadCommand command = threadCaptor.getValue();
        assertThat(command.getThreadName()).isEqualTo("Delivery Review");
        assertThat(command.getStageCode()).isEqualTo("delivery_review");
        assertThat(command.getAgentId()).isEqualTo(31L);
        assertThat(command.getAgentVersionId()).isEqualTo(41L);
        assertThat(command.getInputArtifactId()).isEqualTo(88L);
        assertThat(command.getInputArtifactVersion()).isEqualTo(2L);
        assertThat(command.isRequireAcceptedInputHandoff()).isFalse();
        assertThat(command.isCreateTask()).isTrue();
        assertThat(command.getInputText()).isEqualTo("review delivery");
        ArgumentCaptor<CreateAgentHandoffCommand> handoffCaptor = ArgumentCaptor.forClass(CreateAgentHandoffCommand.class);
        verify(handoffService).createHandoff(handoffCaptor.capture());
        CreateAgentHandoffCommand handoff = handoffCaptor.getValue();
        assertThat(handoff.getSessionId()).isEqualTo(1L);
        assertThat(handoff.getFromThreadId()).isEqualTo(99L);
        assertThat(handoff.getToThreadId()).isEqualTo(100L);
        assertThat(handoff.getArtifactId()).isEqualTo(88L);
        assertThat(handoff.getHandoffType()).isEqualTo("artifact");
        assertThat(handoff.getMessageText()).isEqualTo("Inherited Artifact #88");
        assertThat(result.getStatus()).isEqualTo("continued");
        assertThat(result.getCreatedThreadCount()).isEqualTo(1);
        assertThat(result.getCurrentStageCode()).isEqualTo("delivery_review");
        verify(threadService, never()).startThreadTask(any(), any(), any(StartTaskCommand.class));
    }

    @Test
    void continueAfterGateShouldAutoStartNextStageTaskWhenExplicitlyConfigured() {
        AgentThread current = new AgentThread();
        current.setId(99L);
        current.setThreadName("Intake Review");
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "requiresGate": "intake_confirmed"
                    },
                    {
                      "stageCode": "delivery_review",
                      "stageName": "Delivery Review",
                      "agentId": 31,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "review delivery"
                    }
                  ]
                }
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(current));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH100")
                .taskId(101L)
                .status("pending")
                .build());

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("intake_confirmed")
                .build());

        verify(threadService).startThreadTask(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(100L), any(StartTaskCommand.class));
        assertThat(result.getStatus()).isEqualTo("continued");
        assertThat(result.getCurrentStageCode()).isEqualTo("delivery_review");
    }

    @Test
    void continueAfterGateShouldWaitForAcceptedHandoffBeforeStrictAutoStart() {
        AgentThread current = new AgentThread();
        current.setId(99L);
        current.setThreadName("Intake Review");
        current.setOutputArtifactId(88L);
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "handoffPolicy": {
                    "mode": "strict"
                  },
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "requiresGate": "intake_confirmed"
                    },
                    {
                      "stageCode": "delivery_review",
                      "stageName": "Delivery Review",
                      "agentId": 31,
                      "createTask": true,
                      "autoStartTask": true
                    }
                  ]
                }
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(current));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH100")
                .taskId(101L)
                .status("pending")
                .build());
        AgentHandoff pending = new AgentHandoff();
        pending.setArtifactId(88L);
        pending.setToThreadId(100L);
        pending.setHandoffType("artifact");
        pending.setStatus("pending");
        when(handoffService.listHandoffs(1L)).thenReturn(List.of(), List.of(pending));

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("intake_confirmed")
                .build());

        verify(handoffService).createHandoff(any(CreateAgentHandoffCommand.class));
        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(threadService).createThread(threadCaptor.capture());
        assertThat(threadCaptor.getValue().isRequireAcceptedInputHandoff()).isTrue();
        verify(threadService, never()).startThreadTask(any(), any(), any(StartTaskCommand.class));
        assertThat(result.getStatus()).isEqualTo("continued");
    }

    @Test
    void continueAfterGateShouldAutoStartWhenStrictHandoffAlreadyAccepted() {
        AgentThread current = new AgentThread();
        current.setId(99L);
        current.setThreadName("Intake Review");
        current.setOutputArtifactId(88L);
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {
                  "stages": [
                    {
                      "stageCode": "intake_review",
                      "stageName": "Intake Review",
                      "agentId": 30,
                      "requiresGate": "intake_confirmed"
                    },
                    {
                      "stageCode": "delivery_review",
                      "stageName": "Delivery Review",
                      "agentId": 31,
                      "createTask": true,
                      "autoStartTask": true,
                      "waitForHandoffAcceptance": true
                    }
                  ]
                }
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(current));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH100")
                .taskId(101L)
                .status("pending")
                .build());
        AgentHandoff accepted = new AgentHandoff();
        accepted.setArtifactId(88L);
        accepted.setToThreadId(100L);
        accepted.setHandoffType("artifact");
        accepted.setStatus("accepted");
        when(handoffService.listHandoffs(1L)).thenReturn(List.of(accepted), List.of(accepted));

        strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("intake_confirmed")
                .build());

        verify(handoffService, never()).createHandoff(any(CreateAgentHandoffCommand.class));
        verify(threadService).startThreadTask(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(100L), any(StartTaskCommand.class));
    }

    @Test
    void continueAfterGateShouldNotDuplicateExistingNextStageThread() {
        AgentThread existing = new AgentThread();
        existing.setThreadName("Delivery Review");
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {"stages":[
                  {"stageName":"Intake Review","agentId":30,"requiresGate":"intake_confirmed"},
                  {"stageName":"Delivery Review","agentId":31}
                ]}
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(existing));

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("intake_confirmed")
                .build());

        verify(threadService, never()).createThread(any(CreateAgentThreadCommand.class));
        assertThat(result.getCreatedThreadCount()).isZero();
    }

    @Test
    void continueAfterGateShouldCompleteWhenFinalStageGatePassed() {
        AgentThread finalThread = new AgentThread();
        finalThread.setThreadName("Review And Delivery");
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {"stages":[
                  {"stageCode":"review_and_delivery","stageName":"Review And Delivery","agentId":31,"requiresGate":"delivery_confirmed"}
                ]}
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(finalThread));

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("delivery_confirmed")
                .build());

        assertThat(result.getStatus()).isEqualTo("completed");
        assertThat(result.getCreatedThreadCount()).isZero();
        assertThat(result.getCurrentStageCode()).isEqualTo("review_and_delivery");
        verify(threadService, never()).createThread(any(CreateAgentThreadCommand.class));
    }

    @Test
    void continueAfterGateShouldAdvanceAfterLastExistingStageWhenGateCodeIsShared() {
        AgentThread backendThread = new AgentThread();
        backendThread.setThreadName("Backend Implementation");
        AgentThread frontendThread = new AgentThread();
        frontendThread.setThreadName("Frontend Implementation");
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {"stages":[
                  {
                    "stageCode":"backend_implementation",
                    "stageName":"Backend Implementation",
                    "agentId":30,
                    "requiresGate":"implementation_done"
                  },
                  {
                    "stageCode":"frontend_implementation",
                    "stageName":"Frontend Implementation",
                    "agentId":31,
                    "requiresGate":"implementation_done"
                  },
                  {
                    "stageCode":"testing",
                    "stageName":"Testing",
                    "agentId":32,
                    "createTask":true
                  }
                ]}
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(backendThread, frontendThread));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH100")
                .taskId(101L)
                .status("pending")
                .build());

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("implementation_done")
                .build());

        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(threadService).createThread(threadCaptor.capture());
        assertThat(threadCaptor.getValue().getStageCode()).isEqualTo("testing");
        assertThat(threadCaptor.getValue().getThreadName()).isEqualTo("Testing");
        assertThat(result.getCreatedThreadCount()).isEqualTo(1);
        assertThat(result.getCurrentStageCode()).isEqualTo("testing");
    }

    @Test
    void continueAfterGateShouldCreateHandoffsForAllFanInArtifactsAndWaitBeforeStrictAutoStart() {
        AgentThread backendThread = new AgentThread();
        backendThread.setId(201L);
        backendThread.setThreadName("Backend Implementation");
        backendThread.setOutputArtifactId(701L);
        backendThread.setContextJson("{\"stageCode\":\"backend_implementation\"}");
        AgentThread frontendThread = new AgentThread();
        frontendThread.setId(202L);
        frontendThread.setThreadName("Frontend Implementation");
        frontendThread.setOutputArtifactId(702L);
        frontendThread.setContextJson("{\"stageCode\":\"frontend_implementation\"}");
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {"handoffPolicy":{"mode":"strict"},"stages":[
                  {
                    "stageCode":"backend_implementation",
                    "stageName":"Backend Implementation",
                    "agentId":30,
                    "requiresGate":"implementation_done"
                  },
                  {
                    "stageCode":"frontend_implementation",
                    "stageName":"Frontend Implementation",
                    "agentId":31,
                    "requiresGate":"implementation_done"
                  },
                  {
                    "stageCode":"testing",
                    "stageName":"Testing",
                    "agentId":32,
                    "createTask":true,
                    "autoStartTask":true,
                    "inputArtifactTypes":["implementation_summary"]
                  }
                ]}
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(backendThread, frontendThread));
        when(handoffService.listHandoffs(1L)).thenReturn(List.of());
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(300L)
                .threadCode("TH300")
                .taskId(301L)
                .status("pending")
                .build());

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("implementation_done")
                .build());

        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(threadService).createThread(threadCaptor.capture());
        assertThat(threadCaptor.getValue().getStageCode()).isEqualTo("testing");
        assertThat(threadCaptor.getValue().getInputArtifactId()).isEqualTo(702L);
        assertThat(threadCaptor.getValue().getInputArtifactIds()).containsExactly(701L, 702L);
        assertThat(threadCaptor.getValue().isRequireAcceptedInputHandoff()).isTrue();

        ArgumentCaptor<CreateAgentHandoffCommand> handoffCaptor = ArgumentCaptor.forClass(CreateAgentHandoffCommand.class);
        verify(handoffService, org.mockito.Mockito.times(2)).createHandoff(handoffCaptor.capture());
        assertThat(handoffCaptor.getAllValues()).extracting(CreateAgentHandoffCommand::getFromThreadId)
                .containsExactly(201L, 202L);
        assertThat(handoffCaptor.getAllValues()).extracting(CreateAgentHandoffCommand::getArtifactId)
                .containsExactly(701L, 702L);
        assertThat(handoffCaptor.getAllValues()).extracting(CreateAgentHandoffCommand::getToThreadId)
                .containsExactly(300L, 300L);
        verify(threadService, never()).startThreadTask(any(), any(), any(StartTaskCommand.class));
        assertThat(result.getCreatedThreadCount()).isEqualTo(1);
        assertThat(result.getCurrentStageCode()).isEqualTo("testing");
    }

    @Test
    void continueAfterGateShouldCreateContiguousStagesWithSameGateAsFanOut() {
        AgentThread designThread = new AgentThread();
        designThread.setId(99L);
        designThread.setThreadName("Technical Design");
        designThread.setOutputArtifactId(88L);
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {"stages":[
                  {
                    "stageCode":"technical_design",
                    "stageName":"Technical Design",
                    "agentId":20,
                    "requiresGate":"design_confirmed"
                  },
                  {
                    "stageCode":"backend_implementation",
                    "stageName":"Backend Implementation",
                    "agentId":30,
                    "createTask":true,
                    "autoStartTask":true,
                    "requiresGate":"implementation_done"
                  },
                  {
                    "stageCode":"frontend_implementation",
                    "stageName":"Frontend Implementation",
                    "agentId":31,
                    "createTask":true,
                    "autoStartTask":true,
                    "requiresGate":"implementation_done"
                  },
                  {
                    "stageCode":"testing",
                    "stageName":"Testing",
                    "agentId":32,
                    "createTask":true,
                    "requiresGate":"tests_passed"
                  }
                ]}
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(designThread));
        when(handoffService.listHandoffs(1L)).thenReturn(List.of());
        when(threadService.createThread(any(CreateAgentThreadCommand.class)))
                .thenReturn(AgentThreadResponse.builder()
                        .threadId(100L)
                        .threadCode("TH100")
                        .taskId(101L)
                        .status("pending")
                        .build())
                .thenReturn(AgentThreadResponse.builder()
                        .threadId(101L)
                        .threadCode("TH101")
                        .taskId(102L)
                        .status("pending")
                        .build());

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("design_confirmed")
                .build());

        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(threadService, org.mockito.Mockito.times(2)).createThread(threadCaptor.capture());
        assertThat(threadCaptor.getAllValues()).extracting(CreateAgentThreadCommand::getStageCode)
                .containsExactly("backend_implementation", "frontend_implementation");
        assertThat(threadCaptor.getAllValues()).extracting(CreateAgentThreadCommand::getInputArtifactId)
                .containsExactly(88L, 88L);
        assertThat(result.getCreatedThreadCount()).isEqualTo(2);
        assertThat(result.getCurrentStageCode()).isEqualTo("backend_implementation");
        verify(threadService, org.mockito.Mockito.times(2)).startThreadTask(any(), any(), any(StartTaskCommand.class));
    }

    @Test
    void continueAfterGateShouldNotFailWhenAutomaticHandoffCannotBeCreated() {
        AgentThread current = new AgentThread();
        current.setId(99L);
        current.setThreadName("Intake Review");
        current.setOutputArtifactId(88L);
        when(qualityGateService.hasBlockingFailedGate(1L)).thenReturn(false);
        when(planService.getPlan(10L)).thenReturn(plan("passed", """
                {"stages":[
                  {"stageName":"Intake Review","agentId":30,"requiresGate":"intake_confirmed"},
                  {"stageName":"Delivery Review","agentId":31}
                ]}
                """));
        when(threadService.listThreads(1L)).thenReturn(List.of(current));
        when(threadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(100L)
                .threadCode("TH100")
                .status("pending")
                .build());
        doThrow(new IllegalStateException("handoff audit unavailable"))
                .when(handoffService).createHandoff(any(CreateAgentHandoffCommand.class));

        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(1L)
                .planId(10L)
                .gateCode("intake_confirmed")
                .build());

        verify(threadService).createThread(any(CreateAgentThreadCommand.class));
        verify(handoffService).createHandoff(any(CreateAgentHandoffCommand.class));
        assertThat(result.getStatus()).isEqualTo("continued");
        assertThat(result.getCreatedThreadCount()).isEqualTo(1);
    }

    private CollaborationPlan plan(String validationStatus, String planJson) {
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(10L);
        plan.setTenantId(100L);
        plan.setSessionId(1L);
        plan.setValidationStatus(validationStatus);
        plan.setPlanJson(planJson);
        return plan;
    }
}
