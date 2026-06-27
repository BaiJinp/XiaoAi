package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelGatewaySupportTest {

    private final ModelCallLogService modelCallLogService = mock(ModelCallLogService.class);
    private final ModelGatewaySupport support = new ModelGatewaySupport(modelCallLogService);

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-support")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void validateModelShouldRejectTypeMismatch() {
        ModelConfig model = model("embedding");

        assertThatThrownBy(() -> support.validateModel(model, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model type mismatch");
    }

    @Test
    void validateModelShouldRejectInactiveModel() {
        ModelConfig model = model("chat");
        model.setStatus("disabled");

        assertThatThrownBy(() -> support.validateModel(model, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model config is not active");
    }

    @Test
    void saveLogShouldPopulateTenantTraceAndTruncateSummaries() {
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(88L);
            return true;
        });
        ModelConfig model = model("chat");
        String requestSummary = "A".repeat(1201);
        String responseSummary = "B".repeat(1201);

        ModelCallLog log = support.saveLog(10L, 11L, 12L, model,
                "chat", 3, 5, "success", null,
                requestSummary, responseSummary, 7);

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog savedLog = captor.getValue();
        assertThat(log).isSameAs(savedLog);
        assertThat(savedLog.getId()).isEqualTo(88L);
        assertThat(savedLog.getTenantId()).isEqualTo(100L);
        assertThat(savedLog.getTaskId()).isEqualTo(10L);
        assertThat(savedLog.getRunId()).isEqualTo(11L);
        assertThat(savedLog.getStepId()).isEqualTo(12L);
        assertThat(savedLog.getProviderId()).isEqualTo(2L);
        assertThat(savedLog.getModelId()).isEqualTo(1L);
        assertThat(savedLog.getCallType()).isEqualTo("chat");
        assertThat(savedLog.getPromptTokens()).isEqualTo(3);
        assertThat(savedLog.getCompletionTokens()).isEqualTo(5);
        assertThat(savedLog.getTotalTokens()).isEqualTo(8);
        assertThat(savedLog.getCostAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedLog.getStatus()).isEqualTo("success");
        assertThat(savedLog.getErrorMessage()).isNull();
        assertThat(savedLog.getLatencyMs()).isEqualTo(7);
        assertThat(savedLog.getTraceId()).isEqualTo("trace-support");
        assertThat(savedLog.getRequestSummary()).hasSize(1000).isEqualTo("A".repeat(1000));
        assertThat(savedLog.getResponseSummary()).hasSize(1000).isEqualTo("B".repeat(1000));
    }

    @Test
    void estimateTokensShouldReturnZeroForBlankText() {
        assertThat(support.estimateTokens(null)).isZero();
        assertThat(support.estimateTokens("")).isZero();
        assertThat(support.estimateTokens("   ")).isZero();
    }

    private ModelConfig model(String modelType) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setTenantId(100L);
        model.setProviderId(2L);
        model.setModelType(modelType);
        model.setStatus("active");
        return model;
    }
}
