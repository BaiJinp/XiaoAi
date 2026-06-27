package com.xiaoai.agent.model.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatModelResponse {

    private final String content;

    private final Integer promptTokens;

    private final Integer completionTokens;

    private final Integer totalTokens;

    private final Long modelCallLogId;
}
