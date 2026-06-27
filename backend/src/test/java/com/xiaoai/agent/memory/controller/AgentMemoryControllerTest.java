package com.xiaoai.agent.memory.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.model.AgentMemoryPageQuery;
import com.xiaoai.agent.memory.model.CreateAgentMemoryCommand;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryControllerTest {

    private final AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
    private final AgentMemoryController controller = new AgentMemoryController(agentMemoryService);

    @Test
    void createConfirmedMemoryShouldDelegateToService() {
        CreateAgentMemoryCommand command = new CreateAgentMemoryCommand();
        command.setAgentId(1L);
        command.setSummaryText("confirmed constraint");
        AgentMemory memory = new AgentMemory();
        memory.setId(10L);
        memory.setSummaryText("confirmed constraint");
        when(agentMemoryService.createConfirmedMemory(command)).thenReturn(memory);

        ApiResponse<AgentMemory> response = controller.createConfirmedMemory(command);

        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getData().getSummaryText()).isEqualTo("confirmed constraint");
        verify(agentMemoryService).createConfirmedMemory(command);
    }

    @Test
    void pageMemoriesShouldDelegateToService() {
        AgentMemoryPageQuery query = new AgentMemoryPageQuery();
        when(agentMemoryService.pageMemories(query)).thenReturn(PageResponse.<AgentMemory>builder()
                .pageNo(1)
                .pageSize(20)
                .total(0)
                .records(List.of())
                .build());

        ApiResponse<PageResponse<AgentMemory>> response = controller.pageMemories(query);

        assertThat(response.getData().getRecords()).isEmpty();
        verify(agentMemoryService).pageMemories(query);
    }

    @Test
    void archiveMemoryShouldDelegateToService() {
        AgentMemory memory = new AgentMemory();
        memory.setId(10L);
        memory.setStatus("archived");
        when(agentMemoryService.archiveMemory(10L)).thenReturn(memory);

        ApiResponse<AgentMemory> response = controller.archiveMemory(10L);

        assertThat(response.getData().getStatus()).isEqualTo("archived");
        verify(agentMemoryService).archiveMemory(10L);
    }
}
