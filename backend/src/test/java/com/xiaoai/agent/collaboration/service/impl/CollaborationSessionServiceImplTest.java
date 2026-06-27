package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.mapper.CollaborationSessionMapper;
import com.xiaoai.agent.collaboration.model.CollaborationSessionPageQuery;
import com.xiaoai.agent.collaboration.model.CollaborationSessionResponse;
import com.xiaoai.agent.collaboration.model.CreateCollaborationSessionCommand;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationSessionServiceImplTest {

    private final CollaborationSessionMapper mapper = mock(CollaborationSessionMapper.class);
    private final CollaborationSessionServiceImpl service = new CollaborationSessionServiceImpl();

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
    void createSessionShouldRequireContextAndCreateGenericPlanningSession() {
        when(mapper.insert(any(CollaborationSession.class))).thenAnswer(invocation -> {
            CollaborationSession session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        });
        CreateCollaborationSessionCommand command = new CreateCollaborationSessionCommand();
        command.setTemplateId(2L);
        command.setRootTaskId(3L);
        command.setStrategyType("orchestrated_team");
        command.setGoalText("完成一次通用协作交付");
        command.setContextJson("{\"domain\":\"project_management\"}");

        CollaborationSessionResponse response = service.createSession(command);

        ArgumentCaptor<CollaborationSession> captor = ArgumentCaptor.forClass(CollaborationSession.class);
        verify(mapper).insert(captor.capture());
        CollaborationSession saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getSessionCode()).startsWith("CS");
        assertThat(saved.getTemplateId()).isEqualTo(2L);
        assertThat(saved.getRootTaskId()).isEqualTo(3L);
        assertThat(saved.getStrategyType()).isEqualTo("orchestrated_team");
        assertThat(saved.getGoalText()).isEqualTo("完成一次通用协作交付");
        assertThat(saved.getStatus()).isEqualTo("planning");
        assertThat(saved.getContextJson()).isEqualTo("{\"domain\":\"project_management\"}");
        assertThat(response.getSessionId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo("planning");
    }

    @Test
    void createSessionShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        CreateCollaborationSessionCommand command = new CreateCollaborationSessionCommand();
        command.setStrategyType("orchestrated_team");
        command.setGoalText("完成一次通用协作交付");

        assertThatThrownBy(() -> service.createSession(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(mapper, never()).insert(any(CollaborationSession.class));
    }

    @Test
    void pageSessionsShouldReturnTenantScopedPage() {
        CollaborationSession session = new CollaborationSession();
        session.setId(1L);
        session.setTenantId(100L);
        session.setSessionCode("session_001");
        session.setStatus("planning");
        Page<CollaborationSession> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(session));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        CollaborationSessionPageQuery query = new CollaborationSessionPageQuery();
        query.setStatus("planning");

        PageResponse<CollaborationSession> response = service.pageSessions(query);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getSessionCode()).isEqualTo("session_001");
    }

    @Test
    void getSessionShouldReturnTenantScopedSession() {
        CollaborationSession session = new CollaborationSession();
        session.setId(1L);
        session.setTenantId(100L);
        session.setSessionCode("session_001");
        session.setGoalText("完成一次通用协作交付");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(session);

        CollaborationSession result = service.getSession(1L);

        assertThat(result.getSessionCode()).isEqualTo("session_001");
        assertThat(result.getGoalText()).isEqualTo("完成一次通用协作交付");
    }

    @Test
    void getSessionShouldRejectMissingOrCrossTenantSession() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getSession(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Collaboration session not found");
    }
}
