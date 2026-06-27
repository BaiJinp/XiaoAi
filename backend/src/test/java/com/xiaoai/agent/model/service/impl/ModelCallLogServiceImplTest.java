package com.xiaoai.agent.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.mapper.ModelCallLogMapper;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelCallLogServiceImplTest {

    private final ModelCallLogMapper modelCallLogMapper = mock(ModelCallLogMapper.class);
    private final ModelCallLogServiceImpl modelCallLogService = new ModelCallLogServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(modelCallLogService, modelCallLogMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getModelCallLogShouldReturnTenantScopedLog() {
        ModelCallLog log = new ModelCallLog();
        log.setId(1L);
        log.setTenantId(100L);
        log.setStatus("success");
        when(modelCallLogMapper.selectOne(any(Wrapper.class))).thenReturn(log);

        ModelCallLog result = modelCallLogService.getModelCallLog(1L);

        assertThat(result.getStatus()).isEqualTo("success");
    }

    @Test
    void getModelCallLogShouldRejectMissingOrCrossTenantLog() {
        when(modelCallLogMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> modelCallLogService.getModelCallLog(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model call log not found");
    }
}
