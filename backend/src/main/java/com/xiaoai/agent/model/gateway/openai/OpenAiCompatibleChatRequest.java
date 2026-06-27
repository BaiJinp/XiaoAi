package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiCompatibleChatRequest {

    private final String model;

    private final List<Message> messages;

    private final Double temperature;

    @JsonProperty("max_tokens")
    private final Integer maxTokens;

    @JsonProperty("top_p")
    private final Double topP;

    @Getter
    @Builder
    public static class Message {
        private final String role;
        private final String content;
    }
}
