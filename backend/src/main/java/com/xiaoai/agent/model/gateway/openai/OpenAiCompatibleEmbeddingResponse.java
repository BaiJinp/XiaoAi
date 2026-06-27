package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiCompatibleEmbeddingResponse {

    private List<EmbeddingData> data;

    private Usage usage;

    @Getter
    @Setter
    public static class EmbeddingData {
        private List<Double> embedding;
    }

    @Getter
    @Setter
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
