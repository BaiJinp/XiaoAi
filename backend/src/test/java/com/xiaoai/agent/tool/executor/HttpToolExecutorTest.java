package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpToolExecutorTest {

    @Test
    void executeShouldPostPayloadToRegisteredEndpoint() {
        CapturingHttpClient client = new CapturingHttpClient(new HttpToolResult(200, "{\"rows\":1}"));
        HttpToolExecutor executor = new HttpToolExecutor(client, Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("https://api.example.com/query");
        tool.setSchemaJson("{\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}},\"additionalProperties\":false}");
        tool.setAuthConfigJson("{\"headers\":{\"X-Tenant\":\"demo\"}}");

        String resultJson = executor.execute(tool, command("{\"query\":\"select\"}"));

        assertThat(resultJson).isEqualTo("{\"rows\":1}");
        assertThat(client.uri).isEqualTo(URI.create("https://api.example.com/query"));
        assertThat(client.payloadJson).isEqualTo("{\"query\":\"select\"}");
        assertThat(client.headers).containsEntry("X-Tenant", "demo");
        assertThat(client.timeout).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void executeShouldValidatePayloadSchema() {
        HttpToolExecutor executor = new HttpToolExecutor(new CapturingHttpClient(new HttpToolResult(200, "{}")), Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("https://api.example.com/query");
        tool.setSchemaJson("{\"properties\":{\"action\":{\"type\":\"string\",\"enum\":[\"query\"]}}}");

        assertThatThrownBy(() -> executor.execute(tool, command("{\"action\":\"delete\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP payload field enum mismatch: action");
    }

    @Test
    void executeShouldValidatePayloadPatternAndDateTimeFormat() {
        HttpToolExecutor executor = new HttpToolExecutor(new CapturingHttpClient(new HttpToolResult(200, "{\"ok\":true}")), Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("https://api.example.com/query");
        tool.setSchemaJson("""
                {"properties":{
                  "businessId":{"type":"string","pattern":"^BIZ-[0-9]{3}$"},
                  "requestedAt":{"type":"string","format":"date-time"}
                }}
                """);

        assertThatThrownBy(() -> executor.execute(tool, command("{\"businessId\":\"BAD\",\"requestedAt\":\"2026-06-14T10:00:00+08:00\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP payload field pattern mismatch: businessId");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"businessId\":\"BIZ-001\",\"requestedAt\":\"2026-06-14 10:00:00\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP payload field format mismatch: requestedAt");

        String resultJson = executor.execute(tool, command("{\"businessId\":\"BIZ-001\",\"requestedAt\":\"2026-06-14T10:00:00+08:00\"}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldValidateOneOfPayloadSchema() {
        HttpToolExecutor executor = new HttpToolExecutor(new CapturingHttpClient(new HttpToolResult(200, "{\"ok\":true}")), Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("https://api.example.com/query");
        tool.setSchemaJson("""
                {
                  "oneOf": [
                    {
                      "required": ["businessId"],
                      "properties": {"businessId": {"type": "string", "pattern": "^BIZ-[0-9]{3}$"}},
                      "additionalProperties": false
                    },
                    {
                      "required": ["externalNo"],
                      "properties": {"externalNo": {"type": "string", "minLength": 6}},
                      "additionalProperties": false
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> executor.execute(tool, command("{\"businessId\":\"BAD\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP payload field oneOf mismatch: $");

        String resultJson = executor.execute(tool, command("{\"externalNo\":\"EXT-001\"}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldRejectToolCodeOutsideControlledNamespace() {
        HttpToolExecutor executor = new HttpToolExecutor(new CapturingHttpClient(new HttpToolResult(200, "{}")), Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("https://api.example.com/query");
        tool.setToolCode("http.query");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP tool code is not allowed");
    }

    @Test
    void executeShouldRejectPrivateEndpointByDefaultAndAllowWhenExplicitlyConfigured() {
        CapturingHttpClient client = new CapturingHttpClient(new HttpToolResult(200, "{\"ok\":true}"));
        HttpToolExecutor executor = new HttpToolExecutor(client, Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("http://127.0.0.1:8080/query");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP endpoint host is not allowed");

        tool.setAuthConfigJson("{\"allowPrivateNetwork\":true}");
        String resultJson = executor.execute(tool, command("{}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldRejectEndpointHostOrPrivateNetworkOutsidePolicy() {
        HttpToolExecutor executor = new HttpToolExecutor(new CapturingHttpClient(new HttpToolResult(200, "{\"ok\":true}")), Duration.ofSeconds(1), 200);
        ToolConfig hostDrift = httpTool("https://evil.example.com/query");
        hostDrift.setAuthConfigJson("{\"_policy\":{\"allowedScheme\":\"https\",\"allowedHost\":\"api.example.com\"}}");

        assertThatThrownBy(() -> executor.execute(hostDrift, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP endpoint host is outside tool policy");

        ToolConfig privateDrift = httpTool("http://127.0.0.1:8080/query");
        privateDrift.setAuthConfigJson("{\"allowPrivateNetwork\":true,\"_policy\":{\"allowedScheme\":\"http\",\"allowedHost\":\"127.0.0.1\",\"allowPrivateNetwork\":false}}");

        assertThatThrownBy(() -> executor.execute(privateDrift, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP private network is outside tool policy");
    }

    @Test
    void executeShouldReportNonSuccessStatus() {
        HttpToolExecutor executor = new HttpToolExecutor(new CapturingHttpClient(new HttpToolResult(500, "upstream failed")), Duration.ofSeconds(1), 200);
        ToolConfig tool = httpTool("https://api.example.com/query");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP tool failed with status 500: upstream failed");
    }

    @Test
    void executeShouldRedactSensitiveResponseAndErrorDetail() {
        HttpToolExecutor successExecutor = new HttpToolExecutor(
                new CapturingHttpClient(new HttpToolResult(200, "{\"token\":\"abc123\",\"rows\":1}")),
                Duration.ofSeconds(1),
                200
        );
        String resultJson = successExecutor.execute(httpTool("https://api.example.com/query"), command("{}"));
        assertThat(resultJson).isEqualTo("{\"token\":\"***\",\"rows\":1}");

        HttpToolExecutor failedExecutor = new HttpToolExecutor(
                new CapturingHttpClient(new HttpToolResult(500, "Bearer abc.def.ghi")),
                Duration.ofSeconds(1),
                200
        );
        assertThatThrownBy(() -> failedExecutor.execute(httpTool("https://api.example.com/query"), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP tool failed with status 500: Bearer ***");
    }

    private ToolConfig httpTool(String endpointUrl) {
        ToolConfig tool = new ToolConfig();
        tool.setToolType("http");
        tool.setToolCode("controlled.http.query");
        tool.setEndpointUrl(endpointUrl);
        return tool;
    }

    private ExecuteToolCallCommand command(String payloadJson) {
        ExecuteToolCallCommand command = new ExecuteToolCallCommand();
        command.setCallPayloadJson(payloadJson);
        return command;
    }

    private static class CapturingHttpClient implements HttpToolClient {

        private final HttpToolResult result;
        private URI uri;
        private String payloadJson;
        private Map<String, String> headers;
        private Duration timeout;

        CapturingHttpClient(HttpToolResult result) {
            this.result = result;
        }

        @Override
        public HttpToolResult postJson(URI uri, String payloadJson, Map<String, String> headers, Duration timeout) {
            this.uri = uri;
            this.payloadJson = payloadJson;
            this.headers = headers;
            this.timeout = timeout;
            return result;
        }
    }
}
