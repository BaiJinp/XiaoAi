package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiCompatibleSupport {

    private final ObjectMapper objectMapper;

    public OpenAiCompatibleSupport() {
        this(new ObjectMapper());
    }

    public OpenAiCompatibleSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
public String endpoint(String baseUrl, String path) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Model provider base URL is missing");
        }
        String normalized = baseUrl.replaceAll("/+$", "");
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized + path;
    }
public String apiKey(String authConfigJson) {
        try {
            JsonNode root = objectMapper.readTree(StringUtils.hasText(authConfigJson) ? authConfigJson : "{}");
            String apiKey = root.path("apiKey").asText(null);
            if (!StringUtils.hasText(apiKey)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Model provider API key is missing");
            }
            return apiKey;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid model provider auth config JSON");
        }
    }
public OpenAiCompatibleModelOptions options(String configJson) {
        try {
            if (!StringUtils.hasText(configJson)) {
                return new OpenAiCompatibleModelOptions();
            }
            OpenAiCompatibleModelOptions options = objectMapper.readValue(configJson, OpenAiCompatibleModelOptions.class);
            if (options == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid model config JSON");
            }
            return options;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid model config JSON");
        }
    }
}
