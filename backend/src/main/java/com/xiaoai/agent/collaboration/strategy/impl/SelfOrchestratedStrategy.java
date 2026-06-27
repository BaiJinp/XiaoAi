package com.xiaoai.agent.collaboration.strategy.impl;

import com.xiaoai.agent.collaboration.strategy.CollaborationStrategy;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import org.springframework.stereotype.Component;

@Component
public class SelfOrchestratedStrategy implements CollaborationStrategy {

    private final OrchestratedTeamStrategy orchestratedTeamStrategy;

    public SelfOrchestratedStrategy(OrchestratedTeamStrategy orchestratedTeamStrategy) {
        this.orchestratedTeamStrategy = orchestratedTeamStrategy;
    }

    @Override
public String strategyType() {
        return "self_orchestrated";
    }

    @Override
public CollaborationStrategyResult start(CollaborationStrategyContext context) {
        return orchestratedTeamStrategy.start(context);
    }

    @Override
public CollaborationStrategyResult continueAfterGate(CollaborationStrategyContext context) {
        return orchestratedTeamStrategy.continueAfterGate(context);
    }
}
