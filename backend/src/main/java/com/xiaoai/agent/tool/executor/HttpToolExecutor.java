package com.xiaoai.agent.tool.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class HttpToolExecutor implements ToolExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_CHARS = 4000;
    private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private final HttpToolClient httpToolClient;
    private final JsonSchemaPayloadValidator payloadValidator = new JsonSchemaPayloadValidator();
    private final Duration timeout;
    private final int maxResponseChars;

    public HttpToolExecutor() {
        this(new DefaultHttpToolClient(), DEFAULT_TIMEOUT, MAX_RESPONSE_CHARS);
    }

    HttpToolExecutor(HttpToolClient httpToolClient, Duration timeout, int maxResponseChars) {
        this.httpToolClient = httpToolClient;
        this.timeout = timeout;
        this.maxResponseChars = maxResponseChars;
    }

    @Override
public boolean supports(ToolConfig tool) {
        return tool != null && "http".equals(tool.getToolType());
    }

    @Override
public String execute(ToolConfig tool, ExecuteToolCallCommand command) {
        validateToolCode(tool.getToolCode());
        JsonNode authConfig = parseAuthConfig(tool.getAuthConfigJson());
        boolean allowPrivateNetwork = authConfig.path("allowPrivateNetwork").asBoolean(false);
        JsonNode policy = authConfig.path("_policy");
        if (allowPrivateNetwork && policy.isObject() && !policy.path("allowPrivateNetwork").asBoolean(false)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP private network is outside tool policy");
        }
        URI uri = parseEndpoint(tool.getEndpointUrl(), allowPrivateNetwork);
        validateExecutionPolicy(policy, uri);
        String payloadJson = command.getCallPayloadJson() == null ? "{}" : command.getCallPayloadJson();
        JsonNode payload = parseJson(payloadJson, "HTTP payload is invalid JSON");
        validatePayloadSchema(tool.getSchemaJson(), payload);
        Map<String, String> headers = parseHeaders(authConfig);
        HttpToolResult result = httpToolClient.postJson(uri, payloadJson, headers, timeout);
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "HTTP tool failed with status " + result.statusCode()
                            + formatDetail(ToolOutputRedactor.redact(result.body())));
        }
        String body = result.body();
        return body == null || body.isBlank()
                ? "{\"statusCode\":" + result.statusCode() + "}"
                : truncate(ToolOutputRedactor.redact(body), maxResponseChars);
    }

    private void validateToolCode(String toolCode) {
        if (toolCode == null || !toolCode.startsWith("controlled.http.")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP tool code is not allowed");
        }
    }

    private URI parseEndpoint(String endpointUrl, boolean allowPrivateNetwork) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint is required");
        }
        URI uri;
        try {
            uri = URI.create(endpointUrl.trim());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint is invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint scheme is not allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint host is required");
        }
        if (!allowPrivateNetwork && isPrivateEndpoint(uri.getHost())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint host is not allowed");
        }
        return uri;
    }

    private boolean isPrivateEndpoint(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    private void validateExecutionPolicy(JsonNode policy, URI uri) {
        if (!policy.isObject()) {
            return;
        }
        JsonNode allowedHost = policy.path("allowedHost");
        if (allowedHost.isTextual() && !allowedHost.asText().isBlank()
                && !allowedHost.asText().equalsIgnoreCase(uri.getHost())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint host is outside tool policy");
        }
        JsonNode allowedScheme = policy.path("allowedScheme");
        if (allowedScheme.isTextual() && !allowedScheme.asText().isBlank()
                && !allowedScheme.asText().equalsIgnoreCase(uri.getScheme())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP endpoint scheme is outside tool policy");
        }
    }

    private JsonNode parseJson(String json, String errorMessage) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return node == null || node.isMissingNode() ? OBJECT_MAPPER.createObjectNode() : node;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, errorMessage);
        }
    }

    private void validatePayloadSchema(String schemaJson, JsonNode payload) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return;
        }
        JsonNode schema = parseJson(schemaJson, "HTTP schema is invalid JSON");
        payloadValidator.validatePayloadSchema(schema, payload, "HTTP payload");
    }

    private JsonNode parseAuthConfig(String authConfigJson) {
        if (authConfigJson == null || authConfigJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return parseJson(authConfigJson, "HTTP auth config is invalid JSON");
    }

    private Map<String, String> parseHeaders(JsonNode config) {
        if (config == null || config.isMissingNode() || config.isNull()) {
            return Map.of();
        }
        JsonNode headers = config.path("headers");
        if (headers.isMissingNode() || headers.isNull()) {
            return Map.of();
        }
        if (!headers.isObject()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP auth headers must be an object");
        }
        Map<String, String> values = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!HEADER_NAME_PATTERN.matcher(entry.getKey()).matches()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP auth header name is invalid");
            }
            if (!entry.getValue().isTextual()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP auth header value must be text");
            }
            values.put(entry.getKey(), entry.getValue().asText());
        }
        return values;
    }

    private String formatDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        return ": " + truncate(detail.trim(), 500);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
