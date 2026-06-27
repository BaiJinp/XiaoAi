# OpenAI Compatible Model Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add the first real model gateway adapter for OpenAI-compatible chat and embedding APIs while preserving the existing mock gateway and tenant-safe call logging.

**Architecture:** Keep `ModelGateway` as the public boundary used by `ModelGatewayController` and `JavaInProcessRuntimeGateway`. Introduce a routing `ModelGateway` bean that loads the tenant-scoped `ModelConfig` and `ModelProvider`, validates status/type once, then delegates to either the existing mock adapter or a new OpenAI-compatible adapter. The OpenAI-compatible adapter uses Spring Web MVC's built-in HTTP client support, maps the current single-prompt command shape to OpenAI-compatible request bodies, records sanitized call logs, and never logs or returns raw API keys.

**Tech Stack:** Java 17, Spring Boot 3.3.6, MyBatis-Plus 3.5.9, Jackson, Spring `RestClient`, JUnit 5, Mockito, AssertJ, `MockRestServiceServer`.

---

## Non-Negotiable Constraints

1. Do not call a real external model service in automated tests.
2. Do not introduce an OpenAI official SDK or any vendor-specific dependency for this MVP.
3. Do not log, return, or expose `ModelProvider.authConfigJson` or the raw API key.
4. Do not break the existing public API:
   - `POST /api/v1/model-gateway/chat`
   - `POST /api/v1/model-gateway/embedding`
   - `ModelGateway.chat(ChatModelCommand)`
   - `ModelGateway.embedding(EmbeddingModelCommand)`
5. Keep the current mock behavior available for local/demo use.
6. Keep model provider/config reads tenant-scoped through existing services.
7. Follow TDD: failing test first, minimal implementation, verification, then documentation.
8. No Git commit until review, generated tests, full verification, and user confirmation.

---

## Provider Type Rules

Use these provider type values:

- `mock`: existing mock model behavior.
- `openai_compatible`: real OpenAI-compatible HTTP API.

For backward compatibility, treat blank/null provider type as unsupported rather than guessing. Existing UI lets users enter provider type manually; implementation does not need a frontend change in this plan.

---

## OpenAI-Compatible HTTP Mapping

### Chat

Endpoint normalization:

- If `baseUrl` ends with `/v1`, call `{baseUrl}/chat/completions`.
- If `baseUrl` does not end with `/v1`, call `{baseUrl}/v1/chat/completions`.
- Strip trailing slashes before appending paths.

Request:

```json
{
  "model": "<ModelConfig.modelCode>",
  "messages": [
    {"role": "user", "content": "<ChatModelCommand.prompt>"}
  ]
}
```

Optional model params may be read from `ModelConfig.configJson` only when present and valid:

- `temperature`
- `max_tokens`
- `top_p`

Do not fail the call just because `configJson` is `{}`. Invalid JSON should produce a clear `BusinessException(BAD_REQUEST, "Invalid model config JSON")` before calling the remote provider.

Response parsing:

```json
{
  "choices": [
    {"message": {"content": "..."}}
  ],
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 34,
    "total_tokens": 46
  }
}
```

Build `ChatModelResponse` from `choices[0].message.content` and `usage` when present. If usage is absent, estimate prompt/completion tokens with the existing simple `ceil(length / 4.0)` heuristic.

### Embedding

Endpoint normalization:

- If `baseUrl` ends with `/v1`, call `{baseUrl}/embeddings`.
- If `baseUrl` does not end with `/v1`, call `{baseUrl}/v1/embeddings`.

Request:

```json
{
  "model": "<ModelConfig.modelCode>",
  "input": "<EmbeddingModelCommand.input>"
}
```

Response parsing:

```json
{
  "data": [
    {"embedding": [0.1, 0.2, 0.3]}
  ],
  "usage": {
    "prompt_tokens": 10,
    "total_tokens": 10
  }
}
```

Build `EmbeddingModelResponse` from `data[0].embedding` and usage when present. If usage is absent, estimate prompt tokens from input and set total tokens to prompt tokens.

---

## Task 1: Extract Shared Model Validation and Logging

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/ModelGatewaySupport.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/model/gateway/MockModelGateway.java`
- Test: `backend/src/test/java/com/xiaoai/agent/model/gateway/ModelGatewaySupportTest.java`
- Existing reference: `backend/src/test/java/com/xiaoai/agent/model/gateway/MockModelGatewayTest.java`

**Step 1: Write the failing support test**

Create `ModelGatewaySupportTest.java`:

```java
package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelGatewaySupportTest {

    private final ModelCallLogService modelCallLogService = mock(ModelCallLogService.class);
    private final ModelGatewaySupport support = new ModelGatewaySupport(modelCallLogService);

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-real-model")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void validateModelShouldRejectTypeMismatch() {
        ModelConfig model = model("embedding", "active");

        assertThatThrownBy(() -> support.validateModel(model, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model type mismatch");
    }

    @Test
    void validateModelShouldRejectInactiveModel() {
        ModelConfig model = model("chat", "disabled");

        assertThatThrownBy(() -> support.validateModel(model, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model config is not active");
    }

    @Test
    void saveLogShouldPopulateTenantTraceAndTruncateSummaries() {
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(99L);
            return true;
        });
        ModelConfig model = model("chat", "active");

        ModelCallLog log = support.saveLog(10L, 11L, 12L, model,
                "chat", 1, 2, "success", null,
                "A".repeat(1200), "B".repeat(1200), 123);

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog saved = captor.getValue();
        assertThat(log.getId()).isEqualTo(99L);
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getTraceId()).isEqualTo("trace-real-model");
        assertThat(saved.getProviderId()).isEqualTo(2L);
        assertThat(saved.getModelId()).isEqualTo(1L);
        assertThat(saved.getRequestSummary()).hasSize(1000);
        assertThat(saved.getResponseSummary()).hasSize(1000);
    }

    @Test
    void estimateTokensShouldReturnZeroForBlankText() {
        assertThat(support.estimateTokens(" ")).isZero();
        assertThat(support.estimateTokens("abcd")).isEqualTo(1);
        assertThat(support.estimateTokens("abcde")).isEqualTo(2);
    }

    private ModelConfig model(String modelType, String status) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setTenantId(100L);
        model.setProviderId(2L);
        model.setModelType(modelType);
        model.setStatus(status);
        return model;
    }
}
```

**Step 2: Run test to verify it fails**

Run from repository root:

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=ModelGatewaySupportTest test
```

Expected: FAIL because `ModelGatewaySupport` does not exist.

**Step 3: Write minimal implementation**

Create `ModelGatewaySupport.java`:

```java
package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ModelGatewaySupport {

    private final ModelCallLogService modelCallLogService;

    public ModelGatewaySupport(ModelCallLogService modelCallLogService) {
        this.modelCallLogService = modelCallLogService;
    }

    public void validateModel(ModelConfig model, String expectedType) {
        if (!expectedType.equals(model.getModelType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Model type mismatch");
        }
        if (!"active".equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Model config is not active");
        }
    }

    public ModelCallLog saveLog(Long taskId,
                                Long runId,
                                Long stepId,
                                ModelConfig model,
                                String callType,
                                Integer promptTokens,
                                Integer completionTokens,
                                String status,
                                String errorMessage,
                                String requestSummary,
                                String responseSummary,
                                Integer latencyMs) {
        ModelCallLog log = new ModelCallLog();
        log.setTenantId(UserContextHolder.getTenantId());
        log.setTaskId(taskId);
        log.setRunId(runId);
        log.setStepId(stepId);
        log.setProviderId(model.getProviderId());
        log.setModelId(model.getId());
        log.setCallType(callType);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(promptTokens + completionTokens);
        log.setCostAmount(BigDecimal.ZERO);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setLatencyMs(latencyMs);
        log.setTraceId(UserContextHolder.get() == null ? null : UserContextHolder.get().getTraceId());
        log.setRequestSummary(truncate(requestSummary));
        log.setResponseSummary(truncate(responseSummary));
        modelCallLogService.save(log);
        return log;
    }

    public Integer estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0D));
    }

    public Integer elapsedMs(long start) {
        return Math.toIntExact(Math.max(0, System.currentTimeMillis() - start));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
```

**Step 4: Refactor `MockModelGateway` to use support**

Change constructor dependencies:

```java
private final ModelConfigService modelConfigService;
private final ModelGatewaySupport support;

public MockModelGateway(ModelConfigService modelConfigService,
                        ModelGatewaySupport support) {
    this.modelConfigService = modelConfigService;
    this.support = support;
}
```

Replace internal calls:

```java
Integer promptTokens = support.estimateTokens(command.getPrompt());
Integer completionTokens = support.estimateTokens(content);
ModelCallLog log = support.saveLog(..., support.elapsedMs(start));
```

Update `requireModel`:

```java
private ModelConfig requireModel(Long modelId, String expectedType) {
    ModelConfig model = modelConfigService.getModelConfig(modelId);
    support.validateModel(model, expectedType);
    return model;
}
```

Remove duplicated private `saveLog`, `estimateTokens`, `elapsedMs`, and `truncate` methods from `MockModelGateway`.

**Step 5: Update existing mock gateway tests**

In `MockModelGatewayTest`, instantiate support explicitly:

```java
private final ModelGatewaySupport support = new ModelGatewaySupport(modelCallLogService);
private final MockModelGateway modelGateway = new MockModelGateway(modelConfigService, support);
```

**Step 6: Run focused tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=ModelGatewaySupportTest,MockModelGatewayTest test
```

Expected: PASS.

---

## Task 2: Add Provider Lookup to Routing Gateway

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/ModelGatewayAdapter.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/ModelGatewayContext.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/RoutingModelGateway.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/model/gateway/MockModelGateway.java`
- Test: `backend/src/test/java/com/xiaoai/agent/model/gateway/RoutingModelGatewayTest.java`

**Step 1: Write failing routing tests**

Create `RoutingModelGatewayTest.java`:

```java
package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.service.ModelConfigService;
import com.xiaoai.agent.model.service.ModelProviderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingModelGatewayTest {

    private final ModelConfigService modelConfigService = mock(ModelConfigService.class);
    private final ModelProviderService modelProviderService = mock(ModelProviderService.class);
    private final TestAdapter mockAdapter = new TestAdapter("mock");
    private final TestAdapter openAiAdapter = new TestAdapter("openai_compatible");
    private final RoutingModelGateway gateway = new RoutingModelGateway(
            modelConfigService,
            modelProviderService,
            List.of(mockAdapter, openAiAdapter));

    @Test
    void chatShouldRouteByProviderType() {
        ModelConfig model = model("chat", 2L);
        ModelProvider provider = provider("openai_compatible");
        when(modelConfigService.getModelConfig(1L)).thenReturn(model);
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider);
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        ChatModelResponse response = gateway.chat(command);

        assertThat(response.getContent()).isEqualTo("openai_compatible:生成项目周报");
        assertThat(openAiAdapter.chatCalled).isTrue();
        assertThat(mockAdapter.chatCalled).isFalse();
    }

    @Test
    void embeddingShouldRouteByProviderType() {
        ModelConfig model = model("embedding", 2L);
        ModelProvider provider = provider("mock");
        when(modelConfigService.getModelConfig(1L)).thenReturn(model);
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider);
        EmbeddingModelCommand command = new EmbeddingModelCommand();
        command.setModelId(1L);
        command.setInput("项目风险");

        EmbeddingModelResponse response = gateway.embedding(command);

        assertThat(response.getEmbedding()).containsExactly(0.1D);
        assertThat(mockAdapter.embeddingCalled).isTrue();
        assertThat(openAiAdapter.embeddingCalled).isFalse();
    }

    @Test
    void chatShouldRejectUnsupportedProviderType() {
        when(modelConfigService.getModelConfig(1L)).thenReturn(model("chat", 2L));
        when(modelProviderService.getModelProvider(2L)).thenReturn(provider("unknown"));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> gateway.chat(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unsupported model provider type: unknown");
    }

    private ModelConfig model(String modelType, Long providerId) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setProviderId(providerId);
        model.setModelType(modelType);
        model.setStatus("active");
        return model;
    }

    private ModelProvider provider(String providerType) {
        ModelProvider provider = new ModelProvider();
        provider.setId(2L);
        provider.setProviderType(providerType);
        provider.setStatus("active");
        return provider;
    }

    private static final class TestAdapter implements ModelGatewayAdapter {
        private final String providerType;
        private boolean chatCalled;
        private boolean embeddingCalled;

        private TestAdapter(String providerType) {
            this.providerType = providerType;
        }

        @Override
        public String providerType() {
            return providerType;
        }

        @Override
        public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
            chatCalled = true;
            return ChatModelResponse.builder()
                    .content(providerType + ":" + command.getPrompt())
                    .promptTokens(1)
                    .completionTokens(1)
                    .totalTokens(2)
                    .build();
        }

        @Override
        public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
            embeddingCalled = true;
            return EmbeddingModelResponse.builder()
                    .embedding(List.of(0.1D))
                    .promptTokens(1)
                    .totalTokens(1)
                    .build();
        }
    }
}
```

**Step 2: Run test to verify it fails**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=RoutingModelGatewayTest test
```

Expected: FAIL because routing gateway types do not exist.

**Step 3: Create adapter contract**

Create `ModelGatewayAdapter.java`:

```java
package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;

public interface ModelGatewayAdapter {

    String providerType();

    ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context);

    EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context);
}
```

Create `ModelGatewayContext.java`:

```java
package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ModelGatewayContext {

    private final ModelConfig model;

    private final ModelProvider provider;
}
```

**Step 4: Create `RoutingModelGateway`**

```java
package com.xiaoai.agent.model.gateway;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import com.xiaoai.agent.model.service.ModelConfigService;
import com.xiaoai.agent.model.service.ModelProviderService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Primary
public class RoutingModelGateway implements ModelGateway {

    private final ModelConfigService modelConfigService;
    private final ModelProviderService modelProviderService;
    private final Map<String, ModelGatewayAdapter> adapters;

    public RoutingModelGateway(ModelConfigService modelConfigService,
                               ModelProviderService modelProviderService,
                               List<ModelGatewayAdapter> adapters) {
        this.modelConfigService = modelConfigService;
        this.modelProviderService = modelProviderService;
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(ModelGatewayAdapter::providerType, Function.identity()));
    }

    @Override
    public ChatModelResponse chat(ChatModelCommand command) {
        ModelGatewayContext context = context(command.getModelId());
        return adapter(context).chat(command, context);
    }

    @Override
    public EmbeddingModelResponse embedding(EmbeddingModelCommand command) {
        ModelGatewayContext context = context(command.getModelId());
        return adapter(context).embedding(command, context);
    }

    private ModelGatewayContext context(Long modelId) {
        ModelConfig model = modelConfigService.getModelConfig(modelId);
        ModelProvider provider = modelProviderService.getModelProvider(model.getProviderId());
        if (!"active".equals(provider.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Model provider is not active");
        }
        return ModelGatewayContext.builder()
                .model(model)
                .provider(provider)
                .build();
    }

    private ModelGatewayAdapter adapter(ModelGatewayContext context) {
        String providerType = context.getProvider().getProviderType();
        ModelGatewayAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Unsupported model provider type: " + providerType);
        }
        return adapter;
    }
}
```

**Step 5: Convert `MockModelGateway` to adapter and keep direct tests working**

Change class declaration:

```java
@Component
public class MockModelGateway implements ModelGatewayAdapter {
```

Add method:

```java
@Override
public String providerType() {
    return "mock";
}
```

Replace current public `chat(ChatModelCommand)` and `embedding(EmbeddingModelCommand)` signatures with adapter signatures:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
    ModelConfig model = context.getModel();
    support.validateModel(model, "chat");
    ...
}
```

```java
@Override
@Transactional(rollbackFor = Exception.class)
public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
    ModelConfig model = context.getModel();
    support.validateModel(model, "embedding");
    ...
}
```

Update `MockModelGatewayTest` to create a context instead of stubbing `ModelConfigService`. If keeping constructor injection of `ModelConfigService` only for old direct use becomes awkward, remove `ModelConfigService` from `MockModelGateway`; routing owns model lookup now.

Recommended final mock constructor:

```java
public MockModelGateway(ModelGatewaySupport support) {
    this.support = support;
}
```

**Step 6: Run focused tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=RoutingModelGatewayTest,MockModelGatewayTest,ModelGatewaySupportTest test
```

Expected: PASS.

---

## Task 3: Add OpenAI-Compatible Request/Response DTOs and Config Parsing

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleChatRequest.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleChatResponse.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleEmbeddingRequest.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleEmbeddingResponse.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelOptions.java`
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleSupport.java`
- Test: `backend/src/test/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleSupportTest.java`

**Step 1: Write failing support tests**

Create `OpenAiCompatibleSupportTest.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import com.xiaoai.agent.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleSupportTest {

    private final OpenAiCompatibleSupport support = new OpenAiCompatibleSupport();

    @Test
    void endpointShouldAppendV1WhenMissing() {
        assertThat(support.endpoint("https://api.example.com", "/chat/completions"))
                .isEqualTo("https://api.example.com/v1/chat/completions");
    }

    @Test
    void endpointShouldNotDuplicateV1() {
        assertThat(support.endpoint("https://api.example.com/v1/", "/embeddings"))
                .isEqualTo("https://api.example.com/v1/embeddings");
    }

    @Test
    void apiKeyShouldBeReadFromAuthConfigJson() {
        assertThat(support.apiKey("{\"apiKey\":\"sk-test\"}"))
                .isEqualTo("sk-test");
    }

    @Test
    void apiKeyShouldRejectMissingValue() {
        assertThatThrownBy(() -> support.apiKey("{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model provider API key is missing");
    }

    @Test
    void optionsShouldParseAllowedFields() {
        OpenAiCompatibleModelOptions options = support.options("{\"temperature\":0.2,\"max_tokens\":128,\"top_p\":0.9}");

        assertThat(options.getTemperature()).isEqualTo(0.2D);
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(options.getTopP()).isEqualTo(0.9D);
    }

    @Test
    void optionsShouldRejectInvalidJson() {
        assertThatThrownBy(() -> support.options("{"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid model config JSON");
    }
}
```

**Step 2: Run test to verify it fails**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=OpenAiCompatibleSupportTest test
```

Expected: FAIL because support classes do not exist.

**Step 3: Implement options class**

Create `OpenAiCompatibleModelOptions.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAiCompatibleModelOptions {

    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("top_p")
    private Double topP;
}
```

**Step 4: Implement support class**

Create `OpenAiCompatibleSupport.java`:

```java
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
            return objectMapper.readValue(configJson, OpenAiCompatibleModelOptions.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid model config JSON");
        }
    }
}
```

**Step 5: Implement request/response DTOs**

Create `OpenAiCompatibleChatRequest.java`:

```java
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
```

Create `OpenAiCompatibleChatResponse.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OpenAiCompatibleChatResponse {

    private List<Choice> choices;

    private Usage usage;

    @Getter
    @Setter
    public static class Choice {
        private Message message;
    }

    @Getter
    @Setter
    public static class Message {
        private String content;
    }

    @Getter
    @Setter
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
```

Create `OpenAiCompatibleEmbeddingRequest.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OpenAiCompatibleEmbeddingRequest {

    private final String model;

    private final String input;
}
```

Create `OpenAiCompatibleEmbeddingResponse.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
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
```

**Step 6: Run tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=OpenAiCompatibleSupportTest test
```

Expected: PASS.

---

## Task 4: Implement OpenAI-Compatible Adapter Chat Flow

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelGateway.java`
- Test: `backend/src/test/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelGatewayTest.java`

**Step 1: Write failing chat tests**

Create `OpenAiCompatibleModelGatewayTest.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.entity.ModelProvider;
import com.xiaoai.agent.model.gateway.ModelGatewayContext;
import com.xiaoai.agent.model.gateway.ModelGatewaySupport;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleModelGatewayTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final ModelCallLogService modelCallLogService = mock(ModelCallLogService.class);
    private final ModelGatewaySupport gatewaySupport = new ModelGatewaySupport(modelCallLogService);
    private final OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway(
            restClientBuilder.build(),
            gatewaySupport,
            new OpenAiCompatibleSupport(new ObjectMapper()));

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-openai")
                .build());
        when(modelCallLogService.save(any(ModelCallLog.class))).thenAnswer(invocation -> {
            ModelCallLog log = invocation.getArgument(0);
            log.setId(88L);
            return true;
        });
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        server.verify();
    }

    @Test
    void providerTypeShouldBeOpenAiCompatible() {
        assertThat(gateway.providerType()).isEqualTo("openai_compatible");
    }

    @Test
    void chatShouldCallOpenAiCompatibleEndpointAndSaveSuccessLog() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("生成项目周报"))
                .andExpect(jsonPath("$.temperature").value(0.2D))
                .andRespond(withSuccess("""
                        {
                          "choices": [{"message": {"content": "周报内容"}}],
                          "usage": {"prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 12}
                        }
                        """, MediaType.APPLICATION_JSON));
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setTaskId(10L);
        command.setRunId(11L);
        command.setStepId(12L);
        command.setPrompt("生成项目周报");

        ChatModelResponse response = gateway.chat(command, context("https://api.example.com", "{\"temperature\":0.2}"));

        assertThat(response.getContent()).isEqualTo("周报内容");
        assertThat(response.getPromptTokens()).isEqualTo(5);
        assertThat(response.getCompletionTokens()).isEqualTo(7);
        assertThat(response.getTotalTokens()).isEqualTo(12);
        assertThat(response.getModelCallLogId()).isEqualTo(88L);
        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo("success");
        assertThat(log.getRequestSummary()).isEqualTo("生成项目周报");
        assertThat(log.getResponseSummary()).isEqualTo("周报内容");
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void chatShouldSaveFailureLogWithoutApiKeyWhenProviderFails() {
        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-secret"))
                .andRespond(withServerError());
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");

        assertThatThrownBy(() -> gateway.chat(command, context("https://api.example.com", "{}", "sk-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OpenAI-compatible chat call failed");

        ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(modelCallLogService).save(captor.capture());
        ModelCallLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo("failed");
        assertThat(log.getErrorMessage()).doesNotContain("sk-secret");
    }

    private ModelGatewayContext context(String baseUrl, String configJson) {
        return context(baseUrl, configJson, "sk-test");
    }

    private ModelGatewayContext context(String baseUrl, String configJson, String apiKey) {
        ModelConfig model = new ModelConfig();
        model.setId(1L);
        model.setProviderId(2L);
        model.setModelCode("gpt-test");
        model.setModelType("chat");
        model.setConfigJson(configJson);
        model.setStatus("active");
        ModelProvider provider = new ModelProvider();
        provider.setId(2L);
        provider.setProviderType("openai_compatible");
        provider.setBaseUrl(baseUrl);
        provider.setAuthConfigJson("{\"apiKey\":\"" + apiKey + "\"}");
        provider.setStatus("active");
        return ModelGatewayContext.builder()
                .model(model)
                .provider(provider)
                .build();
    }
}
```

**Step 2: Run test to verify it fails**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=OpenAiCompatibleModelGatewayTest test
```

Expected: FAIL because `OpenAiCompatibleModelGateway` does not exist.

**Step 3: Implement chat adapter**

Create `OpenAiCompatibleModelGateway.java`:

```java
package com.xiaoai.agent.model.gateway.openai;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.entity.ModelConfig;
import com.xiaoai.agent.model.gateway.ModelGatewayAdapter;
import com.xiaoai.agent.model.gateway.ModelGatewayContext;
import com.xiaoai.agent.model.gateway.ModelGatewaySupport;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.model.model.EmbeddingModelCommand;
import com.xiaoai.agent.model.model.EmbeddingModelResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class OpenAiCompatibleModelGateway implements ModelGatewayAdapter {

    private final RestClient restClient;
    private final ModelGatewaySupport support;
    private final OpenAiCompatibleSupport openAiSupport;

    public OpenAiCompatibleModelGateway(RestClient.Builder restClientBuilder,
                                        ModelGatewaySupport support,
                                        OpenAiCompatibleSupport openAiSupport) {
        this(restClientBuilder.build(), support, openAiSupport);
    }

    OpenAiCompatibleModelGateway(RestClient restClient,
                                 ModelGatewaySupport support,
                                 OpenAiCompatibleSupport openAiSupport) {
        this.restClient = restClient;
        this.support = support;
        this.openAiSupport = openAiSupport;
    }

    @Override
    public String providerType() {
        return "openai_compatible";
    }

    @Override
    public ChatModelResponse chat(ChatModelCommand command, ModelGatewayContext context) {
        ModelConfig model = context.getModel();
        support.validateModel(model, "chat");
        long start = System.currentTimeMillis();
        try {
            String apiKey = openAiSupport.apiKey(context.getProvider().getAuthConfigJson());
            OpenAiCompatibleModelOptions options = openAiSupport.options(model.getConfigJson());
            OpenAiCompatibleChatRequest request = OpenAiCompatibleChatRequest.builder()
                    .model(model.getModelCode())
                    .messages(List.of(OpenAiCompatibleChatRequest.Message.builder()
                            .role("user")
                            .content(command.getPrompt())
                            .build()))
                    .temperature(options.getTemperature())
                    .maxTokens(options.getMaxTokens())
                    .topP(options.getTopP())
                    .build();
            OpenAiCompatibleChatResponse remoteResponse = restClient.post()
                    .uri(openAiSupport.endpoint(context.getProvider().getBaseUrl(), "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(OpenAiCompatibleChatResponse.class);
            String content = chatContent(remoteResponse);
            int promptTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getPromptTokens() == null
                    ? support.estimateTokens(command.getPrompt())
                    : remoteResponse.getUsage().getPromptTokens();
            int completionTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getCompletionTokens() == null
                    ? support.estimateTokens(content)
                    : remoteResponse.getUsage().getCompletionTokens();
            int totalTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getTotalTokens() == null
                    ? promptTokens + completionTokens
                    : remoteResponse.getUsage().getTotalTokens();
            ModelCallLog log = support.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", promptTokens, completionTokens, "success", null,
                    command.getPrompt(), content, support.elapsedMs(start));
            return ChatModelResponse.builder()
                    .content(content)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .modelCallLogId(log.getId())
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            support.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                    "chat", support.estimateTokens(command.getPrompt()), 0,
                    "failed", sanitize(e.getMessage()), command.getPrompt(), null, support.elapsedMs(start));
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible chat call failed");
        }
    }

    @Override
    public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "OpenAI-compatible embedding is not implemented");
    }

    private String chatContent(OpenAiCompatibleChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().getContent() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible chat response is empty");
        }
        return response.getChoices().get(0).getMessage().getContent();
    }

    private String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
```

**Step 4: Run chat tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=OpenAiCompatibleModelGatewayTest test
```

Expected: PASS for chat tests. If the `MockRestServiceServer` setup requires the same `RestClient.Builder` instance, keep using the constructor that accepts a prebuilt `RestClient` as shown.

---

## Task 5: Implement OpenAI-Compatible Embedding Flow

**Files:**
- Modify: `backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelGateway.java`
- Modify: `backend/src/test/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelGatewayTest.java`

**Step 1: Add failing embedding success test**

Append to `OpenAiCompatibleModelGatewayTest`:

```java
@Test
void embeddingShouldCallOpenAiCompatibleEndpointAndSaveSuccessLog() {
    server.expect(requestTo("https://api.example.com/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer sk-test"))
            .andExpect(jsonPath("$.model").value("embedding-test"))
            .andExpect(jsonPath("$.input").value("项目风险"))
            .andRespond(withSuccess("""
                    {
                      "data": [{"embedding": [0.1, 0.2, 0.3]}],
                      "usage": {"prompt_tokens": 4, "total_tokens": 4}
                    }
                    """, MediaType.APPLICATION_JSON));
    EmbeddingModelCommand command = new EmbeddingModelCommand();
    command.setModelId(1L);
    command.setTaskId(10L);
    command.setRunId(11L);
    command.setStepId(12L);
    command.setInput("项目风险");

    EmbeddingModelResponse response = gateway.embedding(command, embeddingContext("https://api.example.com"));

    assertThat(response.getEmbedding()).containsExactly(0.1D, 0.2D, 0.3D);
    assertThat(response.getPromptTokens()).isEqualTo(4);
    assertThat(response.getTotalTokens()).isEqualTo(4);
    assertThat(response.getModelCallLogId()).isEqualTo(88L);
    ArgumentCaptor<ModelCallLog> captor = ArgumentCaptor.forClass(ModelCallLog.class);
    verify(modelCallLogService).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("success");
    assertThat(captor.getValue().getCallType()).isEqualTo("embedding");
}

private ModelGatewayContext embeddingContext(String baseUrl) {
    ModelConfig model = new ModelConfig();
    model.setId(1L);
    model.setProviderId(2L);
    model.setModelCode("embedding-test");
    model.setModelType("embedding");
    model.setConfigJson("{}");
    model.setStatus("active");
    ModelProvider provider = new ModelProvider();
    provider.setId(2L);
    provider.setProviderType("openai_compatible");
    provider.setBaseUrl(baseUrl);
    provider.setAuthConfigJson("{\"apiKey\":\"sk-test\"}");
    provider.setStatus("active");
    return ModelGatewayContext.builder()
            .model(model)
            .provider(provider)
            .build();
}
```

**Step 2: Run test to verify it fails**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=OpenAiCompatibleModelGatewayTest#embeddingShouldCallOpenAiCompatibleEndpointAndSaveSuccessLog test
```

Expected: FAIL because embedding currently throws not implemented.

**Step 3: Implement embedding**

Replace the temporary `embedding` implementation:

```java
@Override
public EmbeddingModelResponse embedding(EmbeddingModelCommand command, ModelGatewayContext context) {
    ModelConfig model = context.getModel();
    support.validateModel(model, "embedding");
    long start = System.currentTimeMillis();
    try {
        String apiKey = openAiSupport.apiKey(context.getProvider().getAuthConfigJson());
        OpenAiCompatibleEmbeddingRequest request = OpenAiCompatibleEmbeddingRequest.builder()
                .model(model.getModelCode())
                .input(command.getInput())
                .build();
        OpenAiCompatibleEmbeddingResponse remoteResponse = restClient.post()
                .uri(openAiSupport.endpoint(context.getProvider().getBaseUrl(), "/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(OpenAiCompatibleEmbeddingResponse.class);
        List<Double> embedding = embedding(remoteResponse);
        int promptTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getPromptTokens() == null
                ? support.estimateTokens(command.getInput())
                : remoteResponse.getUsage().getPromptTokens();
        int totalTokens = remoteResponse.getUsage() == null || remoteResponse.getUsage().getTotalTokens() == null
                ? promptTokens
                : remoteResponse.getUsage().getTotalTokens();
        ModelCallLog log = support.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                "embedding", promptTokens, 0, "success", null,
                command.getInput(), embedding.toString(), support.elapsedMs(start));
        return EmbeddingModelResponse.builder()
                .embedding(embedding)
                .promptTokens(promptTokens)
                .totalTokens(totalTokens)
                .modelCallLogId(log.getId())
                .build();
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        support.saveLog(command.getTaskId(), command.getRunId(), command.getStepId(), model,
                "embedding", support.estimateTokens(command.getInput()), 0,
                "failed", sanitize(e.getMessage()), command.getInput(), null, support.elapsedMs(start));
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible embedding call failed");
    }
}

private List<Double> embedding(OpenAiCompatibleEmbeddingResponse response) {
    if (response == null || response.getData() == null || response.getData().isEmpty()
            || response.getData().get(0).getEmbedding() == null
            || response.getData().get(0).getEmbedding().isEmpty()) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OpenAI-compatible embedding response is empty");
    }
    return response.getData().get(0).getEmbedding();
}
```

Add import:

```java
import java.util.List;
```

**Step 4: Run adapter tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=OpenAiCompatibleModelGatewayTest test
```

Expected: PASS.

---

## Task 6: Wire Routing Gateway Through Existing Controller and Runtime

**Files:**
- Modify: `backend/src/main/java/com/xiaoai/agent/model/gateway/RoutingModelGateway.java`
- Modify: `backend/src/test/java/com/xiaoai/agent/model/controller/ModelGatewayControllerTest.java` if it exists; otherwise create it.
- Modify: `backend/src/test/java/com/xiaoai/agent/runtime/gateway/JavaInProcessRuntimeGatewayTest.java` only if bean ambiguity or constructor changes break it.

**Step 1: Write or update controller test for routing bean behavior**

If `ModelGatewayControllerTest.java` exists, add a test that mocks `ModelGateway` and verifies controller delegates unchanged.

If it does not exist, create minimal test:

```java
package com.xiaoai.agent.model.controller;

import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelCommand;
import com.xiaoai.agent.model.model.ChatModelResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelGatewayControllerTest {

    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final ModelGatewayController controller = new ModelGatewayController(modelGateway);

    @Test
    void chatShouldDelegateToModelGateway() {
        ChatModelCommand command = new ChatModelCommand();
        command.setModelId(1L);
        command.setPrompt("生成项目周报");
        when(modelGateway.chat(command)).thenReturn(ChatModelResponse.builder()
                .content("ok")
                .promptTokens(1)
                .completionTokens(1)
                .totalTokens(2)
                .build());

        assertThat(controller.chat(command).getData().getContent()).isEqualTo("ok");
    }
}
```

**Step 2: Run controller test**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=ModelGatewayControllerTest test
```

Expected: PASS after controller test compiles. If it fails due to constructor access, check Lombok `@RequiredArgsConstructor` and package access.

**Step 3: Run gateway group tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=ModelGatewaySupportTest,MockModelGatewayTest,RoutingModelGatewayTest,OpenAiCompatibleSupportTest,OpenAiCompatibleModelGatewayTest,ModelGatewayControllerTest test
```

Expected: PASS.

**Step 4: Confirm Spring bean uniqueness manually by test output**

If any Spring context test fails with multiple `ModelGateway` beans:

- Ensure only `RoutingModelGateway` implements `ModelGateway`.
- Ensure `MockModelGateway` implements `ModelGatewayAdapter`, not `ModelGateway`.
- Keep `RoutingModelGateway` annotated with `@Primary` anyway for clarity.

---

## Task 7: Add Documentation and Task-Document Sync

**Files:**
- Modify: `docs/design/20260604_企业数字员工平台_技术.md`
- Modify: `docs/task/20260604_企业数字员工平台_任务.md`
- Optional Modify: `docs/req/20260604_企业数字员工平台_需求.md` only if user-visible requirement wording needs clarification.

**Step 1: Update technical design**

Add a subsection under the Model/Runtime integration area:

```markdown
### OpenAI 兼容真实模型接入

模型调用仍通过 `ModelGateway` 统一入口进入。运行时和控制器不直接感知具体供应商；`RoutingModelGateway` 根据租户内 `ModelConfig.providerId` 读取 `ModelProvider.providerType`，再路由到具体适配器。

首批支持：
- `mock`：保留本地演示和测试行为。
- `openai_compatible`：通过 HTTP 调用 OpenAI 兼容 `/v1/chat/completions` 与 `/v1/embeddings`。

安全约束：
- API Key 仅从 `ModelProvider.authConfigJson` 读取并放入请求头。
- 调用日志只记录请求/响应摘要、token、状态、耗时和错误摘要，不记录 API Key。
- 自动化测试只使用本地 mock HTTP server，不调用外部模型服务。
```

**Step 2: Update task document artifact/status**

In `docs/task/20260604_企业数字员工平台_任务.md`:

- Add the plan file to `## 3. 产物清单`.
- Keep `真实模型/知识文件/HTTP 工具联调` unchecked until implementation and tests finish.
- Add a new submission record after implementation, not before:

```markdown
| 2026-06-11 | 真实模型接入 | 完成 OpenAI 兼容模型网关路由、chat/embedding HTTP 适配、失败日志和无外部网络单元测试；后端定向测试通过。 |
```

**Step 3: Run markdown/document sanity check**

No dedicated markdown test exists in current context. Re-open only the edited sections manually or rely on exact edit result.

---

## Task 8: Review, Generate/Update Tests, and Verify

**Files:**
- Review all files changed by Tasks 1-7.
- Tests are the new/updated backend tests above.

**Step 1: Run backend focused model tests**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=ModelGatewaySupportTest,MockModelGatewayTest,RoutingModelGatewayTest,OpenAiCompatibleSupportTest,OpenAiCompatibleModelGatewayTest,ModelGatewayControllerTest test
```

Expected: PASS.

**Step 2: Run backend full test suite**

```bash
cd backend && /e/Work/maven/apache-maven-3.9.9/bin/mvn.cmd test
```

Expected: existing full backend suite passes. Testcontainers tests may be skipped when Docker is unavailable; report skip count honestly.

**Step 3: Run frontend checks only if frontend was changed**

This implementation plan does not require frontend changes. If a frontend change is made anyway, run:

```bash
cd frontend && npm run typecheck
cd frontend && npm test
```

Expected: PASS. Current Vitest config has `testTimeout: 10000`.

**Step 4: Run code review gate**

Use `/proj-review` according to project rules after code implementation. Fix any findings before claiming the implementation is complete.

**Step 5: Run generated-test gate**

Use `/proj-gen-test` according to project rules. If it identifies missing tests, add them and rerun the relevant commands.

**Step 6: Update persistent resume memory only after verified implementation**

After implementation and tests are verified, update:

- `C:\Users\jinpeng.bai\.claude\projects\E--WorkTree-Agent-xiaoAI\memory\digital-employee-platform-resume-point.md`
- `C:\Users\jinpeng.bai\.claude\projects\E--WorkTree-Agent-xiaoAI\memory\MEMORY.md` only if the hook line changes.

Include:

- OpenAI-compatible adapter implemented or partial status.
- Exact test commands and results.
- Security note that API keys remain sensitive and must not be logged/exposed.

---

## Implementation Order Checklist

1. `ModelGatewaySupport` tests and implementation.
2. Refactor `MockModelGateway` to adapter style without behavior change.
3. `RoutingModelGateway` tests and implementation.
4. OpenAI-compatible DTO/support tests and implementation.
5. OpenAI-compatible chat tests and implementation.
6. OpenAI-compatible embedding tests and implementation.
7. Controller/runtime wiring verification.
8. Technical/task document sync.
9. `/proj-review`.
10. `/proj-gen-test`.
11. Backend focused tests.
12. Backend full tests.
13. User confirmation.
14. Git commit only after confirmation.

---

## Expected Changed Files Summary

```text
backend/src/main/java/com/xiaoai/agent/model/gateway/ModelGatewayAdapter.java
backend/src/main/java/com/xiaoai/agent/model/gateway/ModelGatewayContext.java
backend/src/main/java/com/xiaoai/agent/model/gateway/ModelGatewaySupport.java
backend/src/main/java/com/xiaoai/agent/model/gateway/MockModelGateway.java
backend/src/main/java/com/xiaoai/agent/model/gateway/RoutingModelGateway.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleChatRequest.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleChatResponse.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleEmbeddingRequest.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleEmbeddingResponse.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelGateway.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelOptions.java
backend/src/main/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleSupport.java
backend/src/test/java/com/xiaoai/agent/model/gateway/ModelGatewaySupportTest.java
backend/src/test/java/com/xiaoai/agent/model/gateway/MockModelGatewayTest.java
backend/src/test/java/com/xiaoai/agent/model/gateway/RoutingModelGatewayTest.java
backend/src/test/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleSupportTest.java
backend/src/test/java/com/xiaoai/agent/model/gateway/openai/OpenAiCompatibleModelGatewayTest.java
backend/src/test/java/com/xiaoai/agent/model/controller/ModelGatewayControllerTest.java
docs/design/20260604_企业数字员工平台_技术.md
docs/task/20260604_企业数字员工平台_任务.md
```

---

## Risks and Guardrails

- **Bean ambiguity risk:** avoid by making only `RoutingModelGateway` implement `ModelGateway`; adapters implement `ModelGatewayAdapter`.
- **Secret leakage risk:** never include `authConfigJson` or API key in logs, exceptions returned to clients, request summaries, response summaries, or test assertion output.
- **Provider URL ambiguity:** normalize only `/v1` and do not support arbitrary path templates in MVP.
- **Response variability:** accept missing usage and estimate tokens; reject empty choices/data with a clear business error.
- **Test flakiness:** use `MockRestServiceServer`, not real network calls.
- **Overdesign risk:** do not add streaming, multi-message conversations, tool calling, vision, retry policies, cost accounting, or provider management UI changes in this task.
