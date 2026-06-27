package com.xiaoai.agent.model.gateway.openai;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OpenAiCompatibleEmbeddingRequest {

    private final String model;

    private final String input;
}
