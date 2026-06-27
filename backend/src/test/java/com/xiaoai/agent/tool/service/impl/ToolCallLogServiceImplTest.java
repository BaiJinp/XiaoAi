package com.xiaoai.agent.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolCallLog;
import com.xiaoai.agent.tool.mapper.ToolCallLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolCallLogServiceImplTest {

    private final ToolCallLogMapper toolCallLogMapper = mock(ToolCallLogMapper.class);
    private final ToolCallLogServiceImpl toolCallLogService = new ToolCallLogServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(toolCallLogService, toolCallLogMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getToolCallLogShouldReturnTenantScopedLog() {
        ToolCallLog log = new ToolCallLog();
        log.setId(1L);
        log.setTenantId(100L);
        log.setCallStatus("success");
        when(toolCallLogMapper.selectOne(any(Wrapper.class))).thenReturn(log);

        ToolCallLog result = toolCallLogService.getToolCallLog(1L);

        assertThat(result.getCallStatus()).isEqualTo("success");
    }

    @Test
    void getToolCallLogShouldRejectMissingOrCrossTenantLog() {
        when(toolCallLogMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> toolCallLogService.getToolCallLog(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tool call log not found");
    }
}
