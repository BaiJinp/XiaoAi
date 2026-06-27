package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.mapper.TaskRunMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRunServiceImplTest {

    private final TaskRunMapper taskRunMapper = mock(TaskRunMapper.class);
    private final TaskRunServiceImpl taskRunService = new TaskRunServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(taskRunService, taskRunMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getRunShouldReturnTenantScopedRun() {
        TaskRun run = new TaskRun();
        run.setId(1L);
        run.setTenantId(100L);
        run.setStatus("running");
        when(taskRunMapper.selectOne(any(Wrapper.class))).thenReturn(run);

        TaskRun result = taskRunService.getRun(1L);

        assertThat(result.getStatus()).isEqualTo("running");
    }

    @Test
    void getRunShouldRejectMissingOrCrossTenantRun() {
        when(taskRunMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> taskRunService.getRun(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task run not found");
    }
}
