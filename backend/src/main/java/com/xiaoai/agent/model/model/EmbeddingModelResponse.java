package com.xiaoai.agent.model.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EmbeddingModelResponse {

    private final List<Double> embedding;

    private final Integer promptTokens;

    private final Integer totalTokens;

    private final Long modelCallLogId;
}
