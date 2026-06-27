package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskStep;
import com.xiaoai.agent.task.mapper.TaskStepMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskStepServiceImplTest {

    private final TaskStepMapper taskStepMapper = mock(TaskStepMapper.class);
    private final TaskStepServiceImpl taskStepService = new TaskStepServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(taskStepService, taskStepMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getStepShouldReturnTenantScopedStep() {
        TaskStep step = new TaskStep();
        step.setId(1L);
        step.setTenantId(100L);
        step.setStepName("tool call");
        when(taskStepMapper.selectOne(any(Wrapper.class))).thenReturn(step);

        TaskStep result = taskStepService.getStep(1L);

        assertThat(result.getStepName()).isEqualTo("tool call");
    }

    @Test
    void getStepShouldRejectMissingOrCrossTenantStep() {
        when(taskStepMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> taskStepService.getStep(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task step not found");
    }
}
