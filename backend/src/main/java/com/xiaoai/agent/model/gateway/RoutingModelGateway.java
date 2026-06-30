package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelChunk;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.service.ModelConfigService;
import com.xiaoai.agent.model.service.ModelProviderService;
import com.xiaoai.agent.safety.TokenBudgetTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Primary
public class RoutingModelGateway implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(RoutingModelGateway.class);

    /**
     * 预估每个 token 的字符数（用于估算 token 数量）
     */
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * 预估的安全系数（考虑模型输出可能比输入长）
     */
    private static final double SAFETY_FACTOR = 1.5;

    private final ModelConfigService modelConfigService;
    private final ModelProviderService modelProviderService;
    private final Map<String, ModelGatewayAdapter> adapters;
    private final TokenBudgetTracker budgetTracker;

    public RoutingModelGateway(ModelConfigService modelConfigService,
                               ModelProviderService modelProviderService,
                               List<ModelGatewayAdapter> adapters,
                               TokenBudgetTracker budgetTracker) {
        this.modelConfigService = modelConfigService;
        this.modelProviderService = modelProviderService;
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(ModelGatewayAdapter::providerType, Function.identity()));
        this.budgetTracker = budgetTracker;
    }

    @Override
    public ChatModelResponse chat(ChatModelCommand command) {
        checkBudget(command);

        ModelGatewayContext context = context(command.getModelId());
        ChatModelResponse response = adapter(context).chat(command, context);

        if (response != null && response.getTotalTokens() != null) {
            Long taskId = command.getTaskId();
            if (taskId != null) {
                budgetTracker.recordTaskUsage(taskId, response.getTotalTokens());
            }
            log.info("Model call completed: taskId={}, totalTokens={}",
                    taskId, response.getTotalTokens());
        }

        return response;
    }

    @Override
    public Flux<ChatModelChunk> chatStream(ChatModelCommand command) {
        checkBudget(command);

        ModelGatewayContext context = context(command.getModelId());
        Flux<ChatModelChunk> stream = adapter(context).chatStream(command, context);

        return stream.doOnNext(chunk -> {
            if (chunk.isDone() && chunk.getTotalTokens() != null) {
                Long taskId = command.getTaskId();
                if (taskId != null) {
                    budgetTracker.recordTaskUsage(taskId, chunk.getTotalTokens());
                }
                log.info("Streaming model call completed: taskId={}, totalTokens={}",
                        taskId, chunk.getTotalTokens());
            }
        });
    }

    @Override
    public EmbeddingModelResponse embedding(EmbeddingModelCommand command) {
        ModelGatewayContext context = context(command.getModelId());
        return adapter(context).embedding(command, context);
    }

    private ModelGatewayContext context(Long modelId) {
        ModelConfig model = modelConfigService.getModelConfig(modelId);
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Model config not found");
        }

        ModelProvider provider = modelProviderService.getModelProvider(model.getProviderId());
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Model provider not found");
        }
        if (!"active".equals(provider.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Model provider is not active");
        }

        return ModelGatewayContext.builder()
                .model(model)
                .provider(provider)
                .build();
    }

    private ModelGatewayAdapter adapter(ModelGatewayContext context) {
        String providerType = context.getProvider().getProviderType();
        ModelGatewayAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported model provider type: " + providerType);
        }
        return adapter;
    }

    private void checkBudget(ChatModelCommand command) {
        if (budgetTracker == null || command == null) {
            return;
        }

        long estimatedTokens = estimateTokens(command.getPrompt());
        TokenBudgetTracker.BudgetCheckResult tenantCheck =
                budgetTracker.checkTenantDailyBudget(command.getTenantId(), estimatedTokens);
        if (!tenantCheck.isAllowed()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, tenantCheck.getReason());
        }

        TokenBudgetTracker.BudgetCheckResult taskCheck =
                budgetTracker.checkTaskBudget(command.getTaskId(), estimatedTokens);
        if (!taskCheck.isAllowed()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, taskCheck.getReason());
        }
    }

    private long estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, Math.round((text.length() / (double) CHARS_PER_TOKEN) * SAFETY_FACTOR));
    }
}