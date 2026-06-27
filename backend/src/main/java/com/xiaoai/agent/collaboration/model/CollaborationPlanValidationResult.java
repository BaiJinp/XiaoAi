package com.xiaoai.agent.collaboration.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CollaborationPlanValidationResult {

    private final boolean passed;

    private final List<String> errors;

    public static CollaborationPlanValidationResult passed() {
        return CollaborationPlanValidationResult.builder()
                .passed(true)
                .errors(List.of())
                .build();
    }

    public static CollaborationPlanValidationResult failed(List<String> errors) {
        return CollaborationPlanValidationResult.builder()
                .passed(false)
                .errors(errors == null ? List.of() : errors)
                .build();
    }
}
