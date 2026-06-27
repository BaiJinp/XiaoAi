package com.xiaoai.agent.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.approval.entity.ApprovalRecord;
import com.xiaoai.agent.approval.mapper.ApprovalRecordMapper;
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
import static org.mockito.Mockito.when;

class ApprovalRecordServiceImplTest {

    private final ApprovalRecordMapper approvalRecordMapper = mock(ApprovalRecordMapper.class);
    private final ApprovalRecordServiceImpl approvalRecordService = new ApprovalRecordServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(approvalRecordService, approvalRecordMapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getApprovalRecordShouldReturnTenantScopedRecord() {
        ApprovalRecord record = new ApprovalRecord();
        record.setId(1L);
        record.setTenantId(100L);
        record.setAction("approve");
        when(approvalRecordMapper.selectOne(any(Wrapper.class))).thenReturn(record);

        ApprovalRecord result = approvalRecordService.getApprovalRecord(1L);

        assertThat(result.getAction()).isEqualTo("approve");
    }

    @Test
    void getApprovalRecordShouldRejectMissingOrCrossTenantRecord() {
        when(approvalRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> approvalRecordService.getApprovalRecord(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Approval record not found");
    }
}
