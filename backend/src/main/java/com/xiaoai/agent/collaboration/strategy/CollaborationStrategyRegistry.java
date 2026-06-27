package com.xiaoai.agent.collaboration.strategy;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CollaborationStrategyRegistry {

    private final Map<String, CollaborationStrategy> strategies;

    public CollaborationStrategyRegistry(List<CollaborationStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(CollaborationStrategy::strategyType, Function.identity()));
    }
public CollaborationStrategy getStrategy(String strategyType) {
        CollaborationStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collaboration strategy not found: " + strategyType);
        }
        return strategy;
    }
}
