package com.xiaoai.agent.collaboration.strategy;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollaborationStrategyResult {

    private final String status;

    private final String message;

    private final int createdThreadCount;

    private final int createdGateCount;

    private final String currentStageCode;

    public static CollaborationStrategyResult started(int createdThreadCount, int createdGateCount) {
        return started(createdThreadCount, createdGateCount, null);
    }

    public static CollaborationStrategyResult started(int createdThreadCount, int createdGateCount, String currentStageCode) {
        return CollaborationStrategyResult.builder()
                .status("started")
                .createdThreadCount(createdThreadCount)
                .createdGateCount(createdGateCount)
                .currentStageCode(currentStageCode)
                .build();
    }

    public static CollaborationStrategyResult continued(int createdThreadCount) {
        return continued(createdThreadCount, null);
    }

    public static CollaborationStrategyResult continued(int createdThreadCount, String currentStageCode) {
        return CollaborationStrategyResult.builder()
                .status("continued")
                .createdThreadCount(createdThreadCount)
                .currentStageCode(currentStageCode)
                .build();
    }

    public static CollaborationStrategyResult blocked(String message) {
        return CollaborationStrategyResult.builder()
                .status("blocked")
                .message(message)
                .build();
    }

    public static CollaborationStrategyResult completed(String currentStageCode) {
        return CollaborationStrategyResult.builder()
                .status("completed")
                .currentStageCode(currentStageCode)
                .build();
    }
}
