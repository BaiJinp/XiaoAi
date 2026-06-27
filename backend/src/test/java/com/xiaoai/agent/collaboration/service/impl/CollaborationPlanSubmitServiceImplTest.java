package com.xiaoai.agent.collaboration.service.impl;

import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.mapper.CollaborationPlanMapper;
import com.xiaoai.agent.collaboration.model.CollaborationPlanValidationResult;
import com.xiaoai.agent.collaboration.model.SubmitCollaborationPlanCommand;
import com.xiaoai.agent.collaboration.service.CollaborationPlanValidator;
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

class CollaborationPlanSubmitServiceImplTest {

    private final CollaborationPlanMapper mapper = mock(CollaborationPlanMapper.class);
    private final CollaborationPlanValidator validator = mock(CollaborationPlanValidator.class);
    private final CollaborationPlanServiceImpl service = new CollaborationPlanServiceImpl(validator);

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
    void submitPlanShouldPersistPlanWithPassedValidationResult() {
        when(validator.validate(any())).thenReturn(CollaborationPlanValidationResult.passed());
        when(mapper.insert(any(CollaborationPlan.class))).thenAnswer(invocation -> {
            CollaborationPlan plan = invocation.getArgument(0);
            plan.setId(10L);
            return 1;
        });
        SubmitCollaborationPlanCommand command = new SubmitCollaborationPlanCommand();
        command.setSessionId(20L);
        command.setGeneratedByThreadId(30L);
        command.setPlanJson("{\"goal\":\"完成一次通用协作交付\",\"maxDepth\":1,\"maxThreads\":3,\"stages\":[{\"stageCode\":\"analysis\"}]}");

        CollaborationPlan result = service.submitPlan(command);

        ArgumentCaptor<CollaborationPlan> captor = ArgumentCaptor.forClass(CollaborationPlan.class);
        verify(mapper).insert(captor.capture());
        CollaborationPlan saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getSessionId()).isEqualTo(20L);
        assertThat(saved.getGeneratedByThreadId()).isEqualTo(30L);
        assertThat(saved.getPlanStatus()).isEqualTo("submitted");
        assertThat(saved.getValidationStatus()).isEqualTo("passed");
        assertThat(saved.getValidationResultJson()).contains("\"passed\":true");
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void submitPlanShouldPersistFailedValidationResult() {
        when(validator.validate(any())).thenReturn(CollaborationPlanValidationResult.failed(List.of("goal is required")));
        when(mapper.insert(any(CollaborationPlan.class))).thenAnswer(invocation -> {
            CollaborationPlan plan = invocation.getArgument(0);
            plan.setId(11L);
            return 1;
        });
        SubmitCollaborationPlanCommand command = new SubmitCollaborationPlanCommand();
        command.setSessionId(20L);
        command.setPlanJson("{}");

        CollaborationPlan result = service.submitPlan(command);

        assertThat(result.getValidationStatus()).isEqualTo("failed");
        assertThat(result.getValidationResultJson()).contains("goal is required");
    }

    @Test
    void submitPlanShouldRejectMissingUserContext() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).build());
        SubmitCollaborationPlanCommand command = new SubmitCollaborationPlanCommand();
        command.setSessionId(20L);
        command.setPlanJson("{}");

        assertThatThrownBy(() -> service.submitPlan(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing user context");

        verify(mapper, never()).insert(any(CollaborationPlan.class));
    }
}
