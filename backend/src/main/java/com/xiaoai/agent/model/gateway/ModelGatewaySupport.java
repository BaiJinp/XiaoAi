package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ModelGatewaySupport {

    private final ModelCallLogService modelCallLogService;

    public ModelGatewaySupport(ModelCallLogService modelCallLogService) {
        this.modelCallLogService = modelCallLogService;
    }
public void validateModel(ModelConfig model, String expectedType) {
        if (!expectedType.equals(model.getModelType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Model type mismatch");
        }
        if (!"active".equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Model config is not active");
        }
    }
public ModelCallLog saveLog(Long taskId,
                                Long runId,
                                Long stepId,
                                ModelConfig model,
                                String callType,
                                Integer promptTokens,
                                Integer completionTokens,
                                String status,
                                String errorMessage,
                                String requestSummary,
                                String responseSummary,
                                Integer latencyMs) {
        return saveLog(taskId, runId, stepId, model, callType, promptTokens, completionTokens,
                promptTokens + completionTokens, status, errorMessage, requestSummary, responseSummary, latencyMs);
    }
public ModelCallLog saveLog(Long taskId,
                                Long runId,
                                Long stepId,
                                ModelConfig model,
                                String callType,
                                Integer promptTokens,
                                Integer completionTokens,
                                Integer totalTokens,
                                String status,
                                String errorMessage,
                                String requestSummary,
                                String responseSummary,
                                Integer latencyMs) {
        ModelCallLog log = new ModelCallLog();
        log.setTenantId(UserContextHolder.getTenantId());
        log.setTaskId(taskId);
        log.setRunId(runId);
        log.setStepId(stepId);
        log.setProviderId(model.getProviderId());
        log.setModelId(model.getId());
        log.setCallType(callType);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(totalTokens);
        log.setCostAmount(BigDecimal.ZERO);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setLatencyMs(latencyMs);
        log.setTraceId(UserContextHolder.get() == null ? null : UserContextHolder.get().getTraceId());
        log.setRequestSummary(truncate(requestSummary));
        log.setResponseSummary(truncate(responseSummary));
        modelCallLogService.save(log);
        return log;
    }
public Integer estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0D));
    }
public Integer elapsedMs(long start) {
        return Math.toIntExact(Math.max(0, System.currentTimeMillis() - start));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
