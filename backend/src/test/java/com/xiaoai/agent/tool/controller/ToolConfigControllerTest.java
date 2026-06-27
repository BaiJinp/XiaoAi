package com.xiaoai.agent.tool.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolConfigControllerTest {

    private final ToolConfigService toolConfigService = mock(ToolConfigService.class);
    private final ToolConfigController controller = new ToolConfigController(toolConfigService);

    @Test
    void executeToolCallShouldRejectApprovalBypassedFromHttpEntry() {
        ExecuteToolCallCommand command = new ExecuteToolCallCommand();
        command.setToolId(10L);
        command.setDryRun(false);
        command.setApprovalBypassed(true);

        assertThatThrownBy(() -> controller.executeToolCall(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("approvalBypassed is only allowed for runtime resume");
        verify(toolConfigService, never()).executeToolCall(command);
    }

    @Test
    void executeToolCallShouldDelegateNormalCommand() {
        ExecuteToolCallCommand command = new ExecuteToolCallCommand();
        command.setToolId(10L);
        command.setDryRun(false);
        command.setApprovalBypassed(false);
        when(toolConfigService.executeToolCall(command)).thenReturn(ToolCallExecuteResponse.builder()
                .status("success")
                .toolCallLogId(66L)
                .build());

        ApiResponse<ToolCallExecuteResponse> response = controller.executeToolCall(command);

        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getData().getToolCallLogId()).isEqualTo(66L);
        verify(toolConfigService).executeToolCall(command);
    }
}
