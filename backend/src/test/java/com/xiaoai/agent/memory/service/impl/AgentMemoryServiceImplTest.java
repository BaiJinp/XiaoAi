package com.xiaoai.agent.memory.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.mapper.AgentMemoryMapper;
import com.xiaoai.agent.memory.model.AgentMemoryPageQuery;
import com.xiaoai.agent.memory.model.CreateAgentMemoryCommand;
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

class AgentMemoryServiceImplTest {

    private final AgentMemoryMapper agentMemoryMapper = mock(AgentMemoryMapper.class);
    private final AgentMemoryServiceImpl agentMemoryService = new AgentMemoryServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(agentMemoryService, agentMemoryMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createConfirmedMemoryShouldUseTenantAndDefaultPolicy() {
        when(agentMemoryMapper.insert(any(AgentMemory.class))).thenAnswer(invocation -> {
            AgentMemory memory = invocation.getArgument(0);
            memory.setId(10L);
            return 1;
        });
        CreateAgentMemoryCommand command = new CreateAgentMemoryCommand();
        command.setAgentId(1L);
        command.setAgentVersionId(2L);
        command.setTaskId(3L);
        command.setRunId(4L);
        command.setSummaryText("  用户确认：周报必须按风险优先排序  ");

        AgentMemory result = agentMemoryService.createConfirmedMemory(command);

        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).insert(captor.capture());
        AgentMemory saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getUserId()).isEqualTo(200L);
        assertThat(saved.getAgentId()).isEqualTo(1L);
        assertThat(saved.getAgentVersionId()).isEqualTo(2L);
        assertThat(saved.getTaskId()).isEqualTo(3L);
        assertThat(saved.getRunId()).isEqualTo(4L);
        assertThat(saved.getMemoryCode()).startsWith("MEM-");
        assertThat(saved.getMemoryType()).isEqualTo("session_summary");
        assertThat(saved.getMemoryScope()).isEqualTo("task");
        assertThat(saved.getSummaryText()).isEqualTo("用户确认：周报必须按风险优先排序");
        assertThat(saved.getConfidence()).isEqualTo("confirmed");
        assertThat(saved.getStatus()).isEqualTo("confirmed");
        assertThat(saved.getPolicyJson()).isEqualTo("{\"write\":\"confirmed_only\"}");
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void createConfirmedMemoryShouldRejectBlankSummary() {
        CreateAgentMemoryCommand command = new CreateAgentMemoryCommand();
        command.setAgentId(1L);
        command.setSummaryText(" ");

        assertThatThrownBy(() -> agentMemoryService.createConfirmedMemory(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Memory summary is required");

        verify(agentMemoryMapper, never()).insert(any(AgentMemory.class));
    }

    @Test
    void pageMemoriesShouldReturnConfirmedTenantScopedPageByDefault() {
        AgentMemory memory = new AgentMemory();
        memory.setId(10L);
        memory.setSummaryText("confirmed memory");
        Page<AgentMemory> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(memory));
        when(agentMemoryMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        AgentMemoryPageQuery query = new AgentMemoryPageQuery();
        query.setAgentId(1L);
        query.setTaskId(3L);

        PageResponse<AgentMemory> result = agentMemoryService.pageMemories(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).containsExactly(memory);
        verify(agentMemoryMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void listConfirmedMemoriesForRuntimeShouldIgnoreUnsupportedScopes() {
        List<AgentMemory> result = agentMemoryService.listConfirmedMemoriesForRuntime(
                100L, 1L, 3L, 4L, 200L, List.of("unknown"), 5);

        assertThat(result).isEmpty();
        verify(agentMemoryMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void listConfirmedMemoriesForRuntimeShouldQueryEnabledScopes() {
        AgentMemory memory = new AgentMemory();
        memory.setId(10L);
        when(agentMemoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(memory));

        List<AgentMemory> result = agentMemoryService.listConfirmedMemoriesForRuntime(
                100L, 1L, 3L, 4L, 200L, List.of("task", "session", "agent", "unknown"), 5);

        assertThat(result).containsExactly(memory);
        verify(agentMemoryMapper).selectList(any(Wrapper.class));
    }

    @Test
    void archiveMemoryShouldMarkTenantScopedMemoryArchived() {
        AgentMemory memory = new AgentMemory();
        memory.setId(10L);
        memory.setTenantId(100L);
        memory.setStatus("confirmed");
        when(agentMemoryMapper.selectOne(any(Wrapper.class))).thenReturn(memory);

        AgentMemory result = agentMemoryService.archiveMemory(10L);

        assertThat(result.getStatus()).isEqualTo("archived");
        verify(agentMemoryMapper).updateById(memory);
    }

    @Test
    void archiveMemoryShouldRejectMissingOrCrossTenantMemory() {
        when(agentMemoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> agentMemoryService.archiveMemory(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent memory not found");

        verify(agentMemoryMapper, never()).updateById(any(AgentMemory.class));
    }
}
