package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.model.CollaborationSessionPageQuery;
import com.xiaoai.agent.collaboration.model.CollaborationSessionResponse;
import com.xiaoai.agent.collaboration.model.CreateCollaborationSessionCommand;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationSessionControllerTest {

    private final CollaborationSessionService service = mock(CollaborationSessionService.class);
    private final CollaborationSessionController controller = new CollaborationSessionController(service);

    @Test
    void createSessionShouldDelegateToService() {
        CreateCollaborationSessionCommand command = new CreateCollaborationSessionCommand();
        command.setStrategyType("orchestrated_team");
        command.setGoalText("完成一次通用协作交付");
        CollaborationSessionResponse response = CollaborationSessionResponse.builder()
                .sessionId(10L)
                .sessionCode("CS202606110001")
                .status("planning")
                .build();
        when(service.createSession(command)).thenReturn(response);

        ApiResponse<CollaborationSessionResponse> result = controller.createSession(command);

        assertThat(result.getCode()).isEqualTo("0");
        assertThat(result.getData().getSessionId()).isEqualTo(10L);
        verify(service).createSession(command);
    }

    @Test
    void getByIdShouldReturnSession() {
        CollaborationSession session = new CollaborationSession();
        session.setId(10L);
        session.setSessionCode("CS202606110001");
        when(service.getSession(10L)).thenReturn(session);

        ApiResponse<CollaborationSession> result = controller.getById(10L);

        assertThat(result.getData().getSessionCode()).isEqualTo("CS202606110001");
    }

    @Test
    void pageSessionsShouldReturnPage() {
        CollaborationSession session = new CollaborationSession();
        session.setId(10L);
        session.setSessionCode("CS202606110001");
        CollaborationSessionPageQuery query = new CollaborationSessionPageQuery();
        PageResponse<CollaborationSession> page = PageResponse.<CollaborationSession>builder()
                .pageNo(1)
                .pageSize(20)
                .total(1)
                .records(List.of(session))
                .build();
        when(service.pageSessions(query)).thenReturn(page);

        ApiResponse<PageResponse<CollaborationSession>> result = controller.pageSessions(query);

        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords().get(0).getSessionCode()).isEqualTo("CS202606110001");
    }
}
