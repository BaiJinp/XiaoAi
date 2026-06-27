package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleSupportTest {

    private final OpenAiCompatibleSupport support = new OpenAiCompatibleSupport(new ObjectMapper());

    @Test
    void endpointShouldAppendV1WhenMissing() {
        assertThat(support.endpoint("https://api.example.com", "/chat/completions"))
                .isEqualTo("https://api.example.com/v1/chat/completions");
    }

    @Test
    void endpointShouldStripTrailingSlashAndNotDuplicateV1() {
        assertThat(support.endpoint("https://api.example.com/v1/", "/embeddings"))
                .isEqualTo("https://api.example.com/v1/embeddings");
    }

    @Test
    void endpointShouldRejectMissingBaseUrl() {
        assertThatThrownBy(() -> support.endpoint(" ", "/embeddings"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model provider base URL is missing");
    }

    @Test
    void apiKeyShouldBeReadFromAuthConfigJsonOnlyForCallerUse() {
        assertThat(support.apiKey("{\"apiKey\":\"sk-test\"}"))
                .isEqualTo("sk-test");
    }

    @Test
    void apiKeyShouldRejectMissingValueWithoutExposingSecret() {
        assertThatThrownBy(() -> support.apiKey("{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model provider API key is missing");
    }

    @Test
    void apiKeyShouldRejectInvalidJsonWithoutEchoingInput() {
        assertThatThrownBy(() -> support.apiKey("{\"apiKey\":\"sk-secret\""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid model provider auth config JSON")
                .hasMessageNotContaining("sk-secret");
    }

    @Test
    void optionsShouldParseAllowedFieldsAndIgnoreUnknownFields() {
        OpenAiCompatibleModelOptions options = support.options("{\"temperature\":0.2,\"max_tokens\":128,\"top_p\":0.9,\"ignored\":true}");

        assertThat(options.getTemperature()).isEqualTo(0.2D);
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(options.getTopP()).isEqualTo(0.9D);
    }

    @Test
    void optionsShouldReturnEmptyOptionsForBlankOrEmptyJson() {
        assertThat(support.options(null).getTemperature()).isNull();
        assertThat(support.options("").getMaxTokens()).isNull();
        assertThat(support.options("{}").getTopP()).isNull();
    }

    @Test
    void optionsShouldRejectInvalidJson() {
        assertThatThrownBy(() -> support.options("{"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid model config JSON");
    }

    @Test
    void optionsShouldRejectJsonLiteralNull() {
        assertThatThrownBy(() -> support.options("null"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid model config JSON");
    }

    @Test
    void chatRequestShouldSerializeOpenAiCompatibleFields() throws Exception {
        OpenAiCompatibleChatRequest request = OpenAiCompatibleChatRequest.builder()
                .model("gpt-test")
                .messages(List.of(OpenAiCompatibleChatRequest.Message.builder()
                        .role("user")
                        .content("生成项目周报")
                        .build()))
                .temperature(0.2D)
                .maxTokens(128)
                .topP(0.9D)
                .build();

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"model\":\"gpt-test\"");
        assertThat(json).contains("\"messages\":[{\"role\":\"user\",\"content\":\"生成项目周报\"}]");
        assertThat(json).contains("\"temperature\":0.2");
        assertThat(json).contains("\"max_tokens\":128");
        assertThat(json).contains("\"top_p\":0.9");
    }

    @Test
    void embeddingRequestShouldSerializeOpenAiCompatibleFields() throws Exception {
        OpenAiCompatibleEmbeddingRequest request = OpenAiCompatibleEmbeddingRequest.builder()
                .model("embedding-test")
                .input("项目风险")
                .build();

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"model\":\"embedding-test\"");
        assertThat(json).contains("\"input\":\"项目风险\"");
    }

    @Test
    void chatResponseShouldDeserializeContentAndUsage() throws Exception {
        OpenAiCompatibleChatResponse response = new ObjectMapper().readValue("""
                {
                  "choices": [{"message": {"content": "周报内容"}}],
                  "usage": {"prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 12}
                }
                """, OpenAiCompatibleChatResponse.class);

        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("周报内容");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(5);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(7);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(12);
    }

    @Test
    void embeddingResponseShouldDeserializeEmbeddingAndUsage() throws Exception {
        OpenAiCompatibleEmbeddingResponse response = new ObjectMapper().readValue("""
                {
                  "data": [{"embedding": [0.1, 0.2, 0.3]}],
                  "usage": {"prompt_tokens": 4, "total_tokens": 4}
                }
                """, OpenAiCompatibleEmbeddingResponse.class);

        assertThat(response.getData().get(0).getEmbedding()).containsExactly(0.1D, 0.2D, 0.3D);
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(4);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(4);
    }
}
