package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.mapper.TaskEventMapper;
import com.xiaoai.agent.task.model.TaskEventQuery;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskEventServiceImplTest {

    private final TaskEventMapper taskEventMapper = mock(TaskEventMapper.class);
    private final TaskEventServiceImpl taskEventService = new TaskEventServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(taskEventService, taskEventMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getEventShouldReturnTenantScopedEvent() {
        TaskEvent event = new TaskEvent();
        event.setId(1L);
        event.setTenantId(100L);
        event.setEventType("RUN_STARTED");
        when(taskEventMapper.selectOne(any(Wrapper.class))).thenReturn(event);

        TaskEvent result = taskEventService.getEvent(1L);

        assertThat(result.getEventType()).isEqualTo("RUN_STARTED");
    }

    @Test
    void getEventShouldRejectMissingOrCrossTenantEvent() {
        when(taskEventMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> taskEventService.getEvent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Task event not found");
    }

    @Test
    void listEventsShouldReturnRecentTenantScopedEvents() {
        TaskEvent event = new TaskEvent();
        event.setId(2L);
        event.setTenantId(100L);
        event.setEventType("TOOL_DENIED");
        when(taskEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of(event));

        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("TOOL_DENIED");
        query.setLimit(20);

        List<TaskEvent> result = taskEventService.listEvents(query);

        assertThat(result).containsExactly(event);
        verify(taskEventMapper).selectList(any(Wrapper.class));
    }

    @Test
    void pageEventsShouldReturnTenantScopedPage() {
        TaskEvent event = new TaskEvent();
        event.setId(2L);
        event.setTenantId(100L);
        event.setEventType("TOOL_DENIED");
        Page<TaskEvent> page = new Page<>(2, 10, 25);
        page.setRecords(List.of(event));
        when(taskEventMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("TOOL_DENIED");
        query.setTaskId(10L);
        query.setRunId(20L);
        query.setAgentVersionId(30L);
        query.setKeyword("approval denied");
        query.setPageNo(2);
        query.setPageSize(10);

        PageResponse<TaskEvent> result = taskEventService.pageEvents(query);

        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getRecords()).containsExactly(event);
        verify(taskEventMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void listEventsShouldFilterTaskRunAndKeyword() {
        when(taskEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("TOOL_DENIED");
        query.setTaskId(10L);
        query.setRunId(20L);
        query.setAgentVersionId(30L);
        query.setKeyword("approval denied");
        query.setLimit(30);

        List<TaskEvent> result = taskEventService.listEvents(query);

        assertThat(result).isEmpty();
        verify(taskEventMapper).selectList(any(Wrapper.class));
    }

    @Test
    void listEventsShouldUseDefaultLimit() {
        when(taskEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("TOOL_DENIED");

        List<TaskEvent> result = taskEventService.listEvents(query);

        assertThat(result).isEmpty();
        verify(taskEventMapper).selectList(any(Wrapper.class));
    }

    @Test
    void listEventsShouldCapLargeLimit() {
        when(taskEventMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("TOOL_DENIED");
        query.setLimit(999);

        List<TaskEvent> result = taskEventService.listEvents(query);

        assertThat(result).isEmpty();
        verify(taskEventMapper).selectList(any(Wrapper.class));
    }

    @Test
    void listEventsShouldRejectBlankType() {
        TaskEventQuery query = new TaskEventQuery();
        query.setEventType(" ");

        assertThatThrownBy(() -> taskEventService.listEvents(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Event type is required");
    }

    @Test
    void listEventsShouldRejectUnsupportedEventType() {
        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("RUN_STARTED");

        assertThatThrownBy(() -> taskEventService.listEvents(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unsupported task event query type");
    }

    @Test
    void pageEventsShouldRejectUnsupportedEventType() {
        TaskEventQuery query = new TaskEventQuery();
        query.setEventType("MODEL_CALL");

        assertThatThrownBy(() -> taskEventService.pageEvents(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unsupported task event query type");
    }

    @Test
    void removeToolDeniedBeforeShouldDeleteExpiredDeniedEvents() {
        when(taskEventMapper.delete(any(Wrapper.class))).thenReturn(3);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);

        int removed = taskEventService.removeToolDeniedBefore(cutoff);

        assertThat(removed).isEqualTo(3);
        verify(taskEventMapper).delete(any(Wrapper.class));
    }

    @Test
    void removeToolDeniedBeforeShouldSkipMissingCutoff() {
        int removed = taskEventService.removeToolDeniedBefore(null);

        assertThat(removed).isZero();
        verify(taskEventMapper, never()).delete(any(Wrapper.class));
    }

}
