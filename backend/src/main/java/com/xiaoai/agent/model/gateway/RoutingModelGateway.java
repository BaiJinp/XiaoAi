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
import reactor.core.publisher.Flux;
import com.xiaoai.agent.model.service.ModelConfigService;
import com.xiaoai.agent.model.service.ModelProviderService;
import com.xiaoai.agent.safety.TokenBudgetTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
        // 预算检查
        checkBudget(command);

        ModelGatewayContext context = context(command.getModelId());
        ChatModelResponse response = adapter(context).chat(command, context);

        // 记录实际 token 使用量
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
        // 预算检查
        checkBudget(command);

        ModelGatewayContext context = context(command.getModelId());
        Flux<ChatModelChunk> stream = adapter(context).chatStream(command, context);

        // 在流结束时记录 token 使用量
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

    /**
 