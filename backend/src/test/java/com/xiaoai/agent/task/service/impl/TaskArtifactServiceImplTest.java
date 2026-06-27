package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.mapper.TaskArtifactMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskArtifactServiceImplTest {

    private final TaskArtifactMapper taskArtifactMapper = mock(TaskArtifactMapper.class);
    private final TaskArtifactServiceImpl taskArtifactService = new TaskArtifactServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(taskArtifactService, taskArtifactMapper);
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-artifact")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void listByTaskIdShouldReturnTenantScopedArtifacts() {
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(1L);
        artifact.setTenantId(100L);
        artifact.setTaskId(10L);
        artifact.setArtifactName("项目周报");
        when(taskArtifactMapper.selectList(any(Wrapper.class))).thenReturn(List.of(artifact));

        List<TaskArtifact> artifacts = taskArtifactService.listByTaskId(10L);

        verify(taskArtifactMapper).selectList(any(Wrapper.class));
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).getArtifactName()).isEqualTo("项目周报");
    }

    @Test
    void getArtifactShouldReturnTenantScopedArtifact() {
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(1L);
        artifact.setTenantId(100L);
        artifact.setTaskId(10L);
        artifact.setArtifactName("项目周报");
        when(taskArtifactMapper.selectOne(any(Wrapper.class))).thenReturn(artifact);

        TaskArtifact result = taskArtifactService.getArtifact(1L);

        verify(taskArtifactMapper).selectOne(any(Wrapper.class));
        assertThat(result.getArtifactName()).isEqualTo("项目周报");
    }

    @Test
    void getArtifactShouldRejectMissingOrCrossTenantArtifact() {
        when(taskArtifactMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> taskArtifactService.getArtifact(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task artifact not found");
    }

    @Test
    void listByTaskIdShouldRejectMissingTenantContext() {
        UserContextHolder.clear();

        assertThatThrownBy(() -> taskArtifactService.listByTaskId(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing tenant context");
    }
}
