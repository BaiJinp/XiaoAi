package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.model.StartCollaborationSessionCommand;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategy;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyRegistry;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationSessionStartControllerTest {

    private final CollaborationSessionService sessionService = mock(CollaborationSessionService.class);
    private final CollaborationStrategyRegistry strategyRegistry = mock(CollaborationStrategyRegistry.class);
    private final CollaborationStrategy strategy = mock(CollaborationStrategy.class);
    private final CollaborationSessionStartController controller = new CollaborationSessionStartController(sessionService, strategyRegistry);

    @Test
    void startSessionShouldResolveStrategyFromSessionAndStartWithPlan() {
        CollaborationSession session = new CollaborationSession();
        session.setId(10L);
        session.setStrategyType("orchestrated_team");
        session.setContextJson("{\"domain\":\"project_management\",\"source\":\"api\"}");
        when(sessionService.getSession(10L)).thenReturn(session);
        when(strategyRegistry.getStrategy("orchestrated_team")).thenReturn(strategy);
        when(strategy.start(org.mockito.ArgumentMatchers.any(CollaborationStrategyContext.class)))
                .thenReturn(CollaborationStrategyResult.started(1, 2, "risk_intake"));
        StartCollaborationSessionCommand command = new StartCollaborationSessionCommand();
        command.setPlanId(20L);

        ApiResponse<CollaborationStrategyResult> result = controller.startSession(10L, command);

        ArgumentCaptor<CollaborationStrategyContext> captor = ArgumentCaptor.forClass(CollaborationStrategyContext.class);
        verify(strategy).start(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo(10L);
        assertThat(captor.getValue().getPlanId()).isEqualTo(20L);
        assertThat(result.getData().getStatus()).isEqualTo("started");
        assertThat(result.getData().getCreatedThreadCount()).isEqualTo(1);
        assertThat(result.getData().getCreatedGateCount()).isEqualTo(2);
        ArgumentCaptor<CollaborationSession> sessionCaptor = ArgumentCaptor.forClass(CollaborationSession.class);
        verify(sessionService).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo("running");
        assertThat(sessionCaptor.getValue().getCurrentStageCode()).isEqualTo("risk_intake");
        assertThat(sessionCaptor.getValue().getContextJson()).contains("\"activePlanId\":20");
        assertThat(sessionCaptor.getValue().getContextJson()).contains("\"domain\":\"project_management\"");
        assertThat(sessionCaptor.getValue().getContextJson()).contains("\"source\":\"api\"");
    }

    @Test
    void startSessionShouldRecoverInvalidContextJsonWhenWritingActivePlan() {
        CollaborationSession session = new CollaborationSession();
        session.setId(10L);
        session.setStrategyType("orchestrated_team");
        session.setContextJson("{bad json");
        when(sessionService.getSession(10L)).thenReturn(session);
        when(strategyRegistry.getStrategy("orchestrated_team")).thenReturn(strategy);
        when(strategy.start(org.mockito.ArgumentMatchers.any(CollaborationStrategyContext.class)))
                .thenReturn(CollaborationStrategyResult.started(1, 0, "prd"));
        StartCollaborationSessionCommand command = new StartCollaborationSessionCommand();
        command.setPlanId(20L);

        controller.startSession(10L, command);

        ArgumentCaptor<CollaborationSession> sessionCaptor = ArgumentCaptor.forClass(CollaborationSession.class);
        verify(sessionService).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getContextJson()).isEqualTo("{\"activePlanId\":20}");
    }
}
