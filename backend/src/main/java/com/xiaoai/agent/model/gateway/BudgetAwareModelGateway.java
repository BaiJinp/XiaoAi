package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.safety.TokenBudgetTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 预算感知的模型网关
 * 在模型调用时进行预算检查，超限则拒绝执行
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public class BudgetAwareModelGateway implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(BudgetAwareModelGateway.class);

    /**
     * 预估每个 token 的字符数（用于估算 token 数量）
     */
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * 预估的安全系数（考虑模型输出可能比输入长）
     */
    private static final double SAFETY_FACTOR = 1.5;

    private final ModelGateway delegate;
    private final TokenBudgetTracker budgetTracker;

    public BudgetAwareModelGateway(ModelGateway delegate, TokenBudgetTracker budgetTracker) {
        this.delegate = delegate;
        this.budgetTracker = budgetTracker;
    }

    @Override
public ChatModelResponse chat(ChatModelCommand command) {
        // 估算 token 使用量
        long estimatedTokens = estimateTokenUsage(command.getPrompt());

        Long tenantId = extractTenantId(command);
        Long taskId = command.getTaskId();

        log.debug("Checking budget before model call: tenantId={}, taskId={}, estimatedTokens={}",
                tenantId, taskId, estimatedTokens);

        // 检查租户日预算
        TokenBudgetTracker.BudgetCheckResult tenantCheck = budgetTracker.checkTenantDailyBudget(tenantId, estimatedTokens);
        if (!tenantCheck.isAllowed()) {
            log.warn("Tenant daily budget exceeded: tenantId={}, taskId={}, reason={}",
                    tenantId, taskId, tenantCheck.getReason());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, tenantCheck.getReason());
        }

        // 检查任务预算
        TokenBudgetTracker.BudgetCheckResult taskCheck = budgetTracker.checkTaskBudget(taskId, estimatedTokens);
        if (!taskCheck.isAllowed()) {
            log.warn("Task budget exceeded: tenantId={}, taskId={}, reason={}",
                    tenantId, taskId, taskCheck.getReason());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, taskCheck.getReason());
        }

        // 执行模型调用
        ChatModelResponse response = delegate.chat(command);

        // 记录实际 token 使用量
        if (response != null && response.getTotalTokens() != null) {
            long actualTokens = response.getTotalTokens();
            budgetTracker.recordTenantUsage(tenantId, actualTokens);
            budgetTracker.recordTaskUsage(taskId, actualTokens);

            log.info("Model call completed: tenantId={}, taskId={}, estimatedTokens={}, actualTokens={}",
                    tenantId, taskId, estimatedTokens, actualTokens);
        }

        return response;
    }

    @Override
public EmbeddingModelResponse embedding(EmbeddingModelCommand command) {
        // Embedding 调用暂时不做预算检查
        // 因为 embedding 通常用于知识检索，不是主要的成本来源
        return delegate.embedding(command);
    }

    /**
     * 估算 token 使用量
     *
     * @param prompt 输入提示词
     * @return 预估的 token 数量
     */
    private long estimateTokenUsage(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return 0;
        }

        // 简单估算：字符数 / 4 * 安全系数
        long estimatedTokens = (long) ((prompt.length() / (double) CHARS_PER_TOKEN) * SAFETY_FACTOR);

        // 最小估算值
        return Math.max(estimatedTokens, 100);
    }

    /**
     * 从命令中提取租户ID
     * 目前 ChatModelCommand 没有 tenantId 字段，暂时返回 null
     * 后续可以扩展 ChatModelCommand 添加 tenantId 字段
     */
    private Long extractTenantId(ChatModelCommand command) {
        // TODO: 从 command 中提取 tenantId
        // 目前 ChatModelCommand 没有 tenantId 字段
        // 可以通过 runId 或 taskId 查询对应的 tenantId
        return null;
    }
}
