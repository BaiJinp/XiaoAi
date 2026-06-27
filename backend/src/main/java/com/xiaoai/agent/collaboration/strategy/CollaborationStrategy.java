package com.xiaoai.agent.collaboration.strategy;

public interface CollaborationStrategy {

    String strategyType();

    CollaborationStrategyResult start(CollaborationStrategyContext context);

    CollaborationStrategyResult continueAfterGate(CollaborationStrategyContext context);
}
