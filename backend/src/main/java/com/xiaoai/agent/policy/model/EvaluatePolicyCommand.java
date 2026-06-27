package com.xiaoai.agent.policy.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EvaluatePolicyCommand {

    @NotBlank
    private String targetType;

    private Long targetId;

    private Long applicantUserId;

    private Long fallbackApproverUserId;

    private String contextJson;
}
