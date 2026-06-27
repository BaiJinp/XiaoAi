package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.mapper.CollaborationPlanMapper;
import com.xiaoai.agent.collaboration.service.CollaborationPlanValidator;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationPlanServiceImplTest {

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
    void listPlansShouldReturnTenantAndSessionScopedPlans() {
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(2L);
        plan.setTenantId(100L);
        plan.setSessionId(10L);
        plan.setValidationStatus("passed");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(plan));

        java.util.List<CollaborationPlan> result = service.listPlans(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionId()).isEqualTo(10L);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @Test
    void getPlanShouldReturnTenantScopedPlan() {
        CollaborationPlan plan = new CollaborationPlan();
        plan.setId(1L);
        plan.setTenantId(100L);
        plan.setPlanStatus("draft");
        plan.setValidationStatus("pending");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(plan);

        CollaborationPlan result = service.getPlan(1L);

        assertThat(result.getPlanStatus()).isEqualTo("draft");
        assertThat(result.getValidationStatus()).isEqualTo("pending");
    }

    @Test
    void getPlanShouldRejectMissingOrCrossTenantPlan() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getPlan(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Collaboration plan not found");
    }
}
