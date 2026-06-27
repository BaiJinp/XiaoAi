package com.xiaoai.agent.collaboration.service.impl;

import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategy;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyRegistry;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.task.model.StartTaskCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationGateAdvanceServiceImplTest {

    private final CollaborationSessionService sessionService = mock(CollaborationSessionService.class);
    private final CollaborationPlanService planService = mock(CollaborationPlanService.class);
    private final AgentThreadService agentThreadService = mock(AgentThreadService.class);
    private final CollaborationStrategyRegistry strategyRegistry = mock(CollaborationStrategyRegistry.class);
    private final CollaborationGateAdvanceServiceImpl service = new CollaborationGateAdvanceServiceImpl(
            sessionService,
            planService,
            agentThreadService,
            strategyRegistry
    );

    @Test
    void continueAfterGateShouldAdvanceStrategyAndUpdateCurrentStage() {
        CollaborationSession session = session("{\"owner\":\"qa\",\"activePlanId\":20,\"meta\":{\"source\":\"test\"}}");
        QualityGate gate = gate();
        CollaborationStrategy strategy = mock(CollaborationStrategy.class);
        when(sessionService.getSession(10L)).thenReturn(session);
        when(strategyRegistry.getStrategy("orchestrated_team")).thenReturn(strategy);
        when(strategy.continueAfterGate(any(CollaborationStrategyContext.class)))
                .thenReturn(CollaborationStrategyResult.continued(1, "delivery_review"));

        service.continueAfterGate(10L, gate);

        ArgumentCaptor<CollaborationStrategyContext> contextCaptor = ArgumentCaptor.forClass(CollaborationStrategyContext.class);
        verify(strategy).continueAfterGate(contextCaptor.capture());
        assertThat(contextCaptor.getValue().getSessionId()).isEqualTo(10L);
        assertThat(contextCaptor.getValue().getPlanId()).isEqualTo(20L);
        assertThat(contextCaptor.getValue().getGateCode()).isEqualTo("intake_confirmed");
        verify(sessionService).updateById(session);
        assertThat(session.getCurrentStageCode()).isEqualTo("delivery_review");
    }

    @Test
    void continueAfterGateShouldMarkSessionCompletedWhenStrategyCompleted() {
        CollaborationSession session = session("{\"activePlanId\":20}");
        QualityGate gate = gate();
        gate.setGateCode("delivery_confirmed");
        CollaborationStrategy strategy = mock(CollaborationStrategy.class);
        when(sessionService.getSession(10L)).thenReturn(session);
        when(strategyRegistry.getStrategy("orchestrated_team")).thenReturn(strategy);
        when(strategy.continueAfterGate(any(CollaborationStrategyContext.class)))
                .thenReturn(CollaborationStrategyResult.completed("review_and_delivery"));

        service.continueAfterGate(10L, gate);

        verify(sessionService).updateById(session);
        assertThat(session.getStatus()).isEqualTo("completed");
        assertThat(session.getCurrentStageCode()).isEqualTo("review_and_delivery");
    }

    @Test
    void continueAfterGateShouldSkipWhenNoActivePlanExists() {
        when(sessionService.getSession(10L)).thenReturn(session("{}"));

        service.continueAfterGate(10L, gate());

        verify(strategyRegistry, never()).getStrategy(any());
        verify(sessionService, never()).updateById(any(CollaborationSession.class));
    }

    @Test
    void continueAfterGateShouldSkipWhenGateBelongsToOtherSession() {
        QualityGate gate = gate();
        gate.setSessionId(99L);

        service.continueAfterGate(10L, gate);

        verify(sessionService, never()).getSession(any());
        verify(strategyRegistry, never()).getStrategy(any());
    }

    @Test
    void failAfterGateShouldBlockSessionAndCreateConfiguredReworkThread() {
        CollaborationSession session = session("{\"activePlanId\":20}");
        QualityGate gate = gate();
        gate.setStatus("failed");
        gate.setRequired(true);
        gate.setFailReason("missing acceptance criteria");
        when(sessionService.getSession(10L)).thenReturn(session);
        when(planService.getPlan(20L)).thenReturn(plan("""
                {
                  "stages": [
                    {
                      "stageCode": "requirement_review",
                      "stageName": "Requirement Review",
                      "requiresGate": "intake_confirmed",
                      "reworkStageCode": "requirement_rework"
                    },
                    {
                      "stageCode": "requirement_rework",
                      "stageName": "Requirement Rework",
                      "agentId": 7,
                      "agentVersionId": 8,
                      "roleId": 9,
                      "createTask": true
                    }
                  ]
                }
                """));
        when(agentThreadService.listThreads(10L)).thenReturn(java.util.List.of());
        when(agentThreadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(70L)
                .threadCode("TH070")
                .taskId(71L)
                .status("pending")
                .build());

        service.failAfterGate(10L, gate);

        verify(sessionService).updateById(session);
        assertThat(session.getStatus()).isEqualTo("blocked");
        assertThat(session.getCurrentStageCode()).isEqualTo("requirement_review");
        ArgumentCaptor<CreateAgentThreadCommand> threadCaptor = ArgumentCaptor.forClass(CreateAgentThreadCommand.class);
        verify(agentThreadService).createThread(threadCaptor.capture());
        CreateAgentThreadCommand command = threadCaptor.getValue();
        assertThat(command.getSessionId()).isEqualTo(10L);
        assertThat(command.getAgentId()).isEqualTo(7L);
        assertThat(command.getAgentVersionId()).isEqualTo(8L);
        assertThat(command.getRoleId()).isEqualTo(9L);
        assertThat(command.getStageCode()).isEqualTo("requirement_rework");
        assertThat(command.getThreadName()).isEqualTo("Requirement Rework");
        assertThat(command.isCreateTask()).isTrue();
        assertThat(command.getInputText()).contains("intake_confirmed", "requirement_review", "missing acceptance criteria");
        verify(agentThreadService, never()).startThreadTask(any(), any(), any(StartTaskCommand.class));
    }

    @Test
    void failAfterGateShouldAutoStartConfiguredReworkTask() {
        CollaborationSession session = session("{\"activePlanId\":20}");
        QualityGate gate = gate();
        gate.setRequired(true);
        when(sessionService.getSession(10L)).thenReturn(session);
        when(planService.getPlan(20L)).thenReturn(plan("""
                {
                  "stages": [
                    {
                      "stageCode": "requirement_review",
                      "requiresGate": "intake_confirmed",
                      "reworkStageCode": "requirement_rework"
                    },
                    {
                      "stageCode": "requirement_rework",
                      "stageName": "Requirement Rework",
                      "agentId": 7,
                      "createTask": true,
                      "autoStartTask": true
                    }
                  ]
                }
                """));
        when(agentThreadService.listThreads(10L)).thenReturn(java.util.List.of());
        when(agentThreadService.createThread(any(CreateAgentThreadCommand.class))).thenReturn(AgentThreadResponse.builder()
                .threadId(70L)
                .threadCode("TH070")
                .taskId(71L)
                .status("pending")
                .build());

        service.failAfterGate(10L, gate);

        verify(agentThreadService).startThreadTask(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(70L), any(StartTaskCommand.class));
    }

    @Test
    void failAfterGateShouldOnlyBlockWhenNoReworkStageConfigured() {
        CollaborationSession session = session("{\"activePlanId\":20}");
        QualityGate gate = gate();
        gate.setRequired(true);
        when(sessionService.getSession(10L)).thenReturn(session);
        when(planService.getPlan(20L)).thenReturn(plan("""
                {"stages":[{"stageCode":"requirement_review","requiresGate":"intake_confirmed"}]}
                """));

        service.failAfterGate(10L, gate);

        verify(sessionService).updateById(session);
        assertThat(session.getStatus()).isEqualTo("blocked");
        assertThat(session.getCurrentStageCode()).isEqualTo("requirement_review");
        verify(agentThreadService, never()).createThread(any(CreateAgentThreadCommand.class));
    }

    private QualityGate gate() {
        QualityGate gate = new QualityGate();
        gate.setId(30L);
        gate.setSessionId(10L);
        gate.setGateCode("intake_confirmed");
        gate.setStatus("passed");
        return gate;
    }

    private CollaborationSession session(String contextJson) {
        CollaborationSession session = new CollaborationSession();
        session.setId(10L);
        session.setStrategyType("orchestrated_team");
        session.setContextJson(contextJson);
        return session;
    }

    private CollaborationPlan plan(String planJson) {
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(20L);
        plan.setSessionId(10L);
        plan.setPlanJson(planJson);
        return plan;
    }
}
