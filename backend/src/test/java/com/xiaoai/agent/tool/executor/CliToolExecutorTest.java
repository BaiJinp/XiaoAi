package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliToolExecutorTest {

    @Test
    void executeShouldPassPayloadAsSingleArgumentAndReturnStdout() {
        CapturingRunner runner = new CapturingRunner(new CliProcessResult(0, "{\"rows\":1}", ""));
        CliToolExecutor executor = new CliToolExecutor(runner, Duration.ofSeconds(1), 200);
        ExecuteToolCallCommand command = command("{\"query\":\"select\"}");

        String resultJson = executor.execute(cliTool("E:\\tools\\query.exe"), command);

        assertThat(resultJson).isEqualTo("{\"rows\":1}");
        assertThat(runner.command).containsExactly("E:\\tools\\query.exe", "{\"query\":\"select\"}");
        assertThat(runner.workingDirectory).isNull();
        assertThat(runner.timeout).isEqualTo(Duration.ofSeconds(1));
        assertThat(runner.maxOutputChars).isEqualTo(200);
    }

    @Test
    void executeShouldReturnExitCodeJsonWhenStdoutIsBlank() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, " ", "")),
                Duration.ofSeconds(1),
                200
        );

        String resultJson = executor.execute(cliTool("/usr/local/bin/report"), command(null));

        assertThat(resultJson).isEqualTo("{\"exitCode\":0}");
    }

    @Test
    void executeShouldRejectBlankEndpoint() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );

        assertThatThrownBy(() -> executor.execute(cliTool(" "), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI endpoint is required");
    }

    @Test
    void executeShouldRejectShellExecutable() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );

        assertThatThrownBy(() -> executor.execute(cliTool("powershell.exe"), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Shell execution is not allowed for CLI tools");
    }

    @Test
    void executeShouldRejectToolCodeOutsideControlledNamespace() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setToolCode("cli.query");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI tool code is not allowed");
    }

    @Test
    void executeShouldValidateRequiredPayloadFields() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("{\"required\":[\"query\"]}");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload missing required field: query");
    }

    @Test
    void executeShouldRejectUnsupportedPayloadFieldsWhenAdditionalPropertiesDisabled() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("{\"properties\":{\"query\":{}},\"additionalProperties\":false}");

        assertThatThrownBy(() -> executor.execute(tool, command("{\"query\":\"select\",\"token\":\"abc\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload contains unsupported field: token");
    }

    @Test
    void executeShouldValidatePayloadPropertyTypes() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("{\"properties\":{\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}}");

        assertThatThrownBy(() -> executor.execute(tool, command("{\"query\":123,\"limit\":10}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field type mismatch: query");
    }

    @Test
    void executeShouldAllowSchemaTypeArray() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{\"ok\":true}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("{\"properties\":{\"query\":{\"type\":[\"string\",\"null\"]},\"limit\":{\"type\":\"number\"}}}");

        String resultJson = executor.execute(tool, command("{\"query\":null,\"limit\":1.5}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldValidatePayloadEnumAndStringLength() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("""
                {"properties":{
                  "action":{"type":"string","enum":["query","export"]},
                  "keyword":{"type":"string","minLength":2,"maxLength":8}
                }}
                """);

        assertThatThrownBy(() -> executor.execute(tool, command("{\"action\":\"delete\",\"keyword\":\"risk\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field enum mismatch: action");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"action\":\"query\",\"keyword\":\"x\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field too short: keyword");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"action\":\"query\",\"keyword\":\"very-long-keyword\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field too long: keyword");
    }

    @Test
    void executeShouldValidatePayloadNumberRangeAndArrayLength() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{\"ok\":true}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("""
                {"properties":{
                  "limit":{"type":"integer","minimum":1,"maximum":100},
                  "ids":{"type":"array","minItems":1,"maxItems":3}
                }}
                """);

        assertThatThrownBy(() -> executor.execute(tool, command("{\"limit\":0,\"ids\":[1]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field below minimum: limit");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"limit\":101,\"ids\":[1]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field above maximum: limit");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"limit\":10,\"ids\":[]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field has too few items: ids");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"limit\":10,\"ids\":[1,2,3,4]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field has too many items: ids");

        String resultJson = executor.execute(tool, command("{\"limit\":10,\"ids\":[1,2]}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldValidatePayloadPatternFormatUniqueItemsAndObjectSize() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{\"ok\":true}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("""
                {"properties":{
                  "projectCode":{"type":"string","pattern":"^PRJ-[0-9]{4}$"},
                  "dueDate":{"type":"string","format":"date"},
                  "callbackUrl":{"type":"string","format":"uri"},
                  "emails":{"type":"array","uniqueItems":true,"items":{"type":"string","format":"email"}},
                  "filters":{"type":"object","minProperties":1,"maxProperties":2}
                }}
                """);

        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"BAD-1\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"a@example.com\"],\"filters\":{\"status\":\"open\"}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field pattern mismatch: projectCode");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026/06/14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"a@example.com\"],\"filters\":{\"status\":\"open\"}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field format mismatch: dueDate");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"example.com\",\"emails\":[\"a@example.com\"],\"filters\":{\"status\":\"open\"}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field format mismatch: callbackUrl");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"bad-email\"],\"filters\":{\"status\":\"open\"}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field format mismatch: emails[0]");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"a@example.com\",\"a@example.com\"],\"filters\":{\"status\":\"open\"}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field has duplicate items: emails");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"a@example.com\"],\"filters\":{}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field has too few properties: filters");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"a@example.com\"],\"filters\":{\"status\":\"open\",\"owner\":\"pm\",\"priority\":\"high\"}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field has too many properties: filters");

        String resultJson = executor.execute(tool, command("{\"projectCode\":\"PRJ-2026\",\"dueDate\":\"2026-06-14\",\"callbackUrl\":\"https://example.com\",\"emails\":[\"a@example.com\",\"b@example.com\"],\"filters\":{\"status\":\"open\",\"owner\":\"pm\"}}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldValidateNestedObjectsAndArrayItems() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{\"ok\":true}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setSchemaJson("""
                {
                  "properties": {
                    "customer": {
                      "type": "object",
                      "required": ["id"],
                      "properties": {
                        "id": {"type": "string"},
                        "address": {
                          "type": "object",
                          "properties": {
                            "city": {"type": "string"}
                          },
                          "additionalProperties": false
                        }
                      }
                    },
                    "items": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["sku"],
                        "properties": {
                          "sku": {"type": "string"},
                          "quantity": {"type": "integer", "minimum": 1}
                        },
                        "additionalProperties": false
                      }
                    }
                  }
                }
                """);

        assertThatThrownBy(() -> executor.execute(tool, command("{\"customer\":{\"address\":{\"city\":\"SZ\"}},\"items\":[]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload missing required field: customer.id");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"customer\":{\"id\":\"C1\",\"address\":{\"city\":\"SZ\",\"token\":\"x\"}},\"items\":[]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload contains unsupported field: customer.address.token");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"customer\":{\"id\":\"C1\"},\"items\":[{\"quantity\":1}]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload missing required field: items[0].sku");
        assertThatThrownBy(() -> executor.execute(tool, command("{\"customer\":{\"id\":\"C1\"},\"items\":[{\"sku\":\"A1\",\"quantity\":0}]}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field below minimum: items[0].quantity");

        String resultJson = executor.execute(tool, command("{\"customer\":{\"id\":\"C1\",\"address\":{\"city\":\"SZ\"}},\"items\":[{\"sku\":\"A1\",\"quantity\":2}]}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldValidateOneOfAndAllOfPayloadSchemas() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{\"ok\":true}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig oneOfTool = cliTool("/usr/local/bin/report");
        oneOfTool.setSchemaJson("""
                {
                  "oneOf": [
                    {
                      "required": ["projectCode"],
                      "properties": {"projectCode": {"type": "string", "pattern": "^PRJ-[0-9]{4}$"}},
                      "additionalProperties": false
                    },
                    {
                      "required": ["userEmail"],
                      "properties": {"userEmail": {"type": "string", "format": "email"}},
                      "additionalProperties": false
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> executor.execute(oneOfTool, command("{\"projectCode\":\"BAD\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field oneOf mismatch: $");
        assertThatThrownBy(() -> executor.execute(oneOfTool, command("{\"projectCode\":\"PRJ-2026\",\"userEmail\":\"a@example.com\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field oneOf mismatch: $");
        assertThat(executor.execute(oneOfTool, command("{\"projectCode\":\"PRJ-2026\"}"))).isEqualTo("{\"ok\":true}");

        ToolConfig allOfTool = cliTool("/usr/local/bin/report");
        allOfTool.setSchemaJson("""
                {
                  "allOf": [
                    {"required": ["projectCode"]},
                    {"properties": {"priority": {"type": "integer", "minimum": 1}}}
                  ]
                }
                """);

        assertThatThrownBy(() -> executor.execute(allOfTool, command("{\"priority\":1}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload missing required field: projectCode");
        assertThatThrownBy(() -> executor.execute(allOfTool, command("{\"projectCode\":\"PRJ-2026\",\"priority\":0}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI payload field below minimum: priority");
        assertThat(executor.execute(allOfTool, command("{\"projectCode\":\"PRJ-2026\",\"priority\":1}"))).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldPassConfiguredWorkingDirectory() {
        CapturingRunner runner = new CapturingRunner(new CliProcessResult(0, "{\"ok\":true}", ""));
        CliToolExecutor executor = new CliToolExecutor(runner, Duration.ofSeconds(1), 200);
        ToolConfig tool = cliTool("/usr/local/bin/report");
        Path workingDirectory = Path.of("").toAbsolutePath();
        tool.setAuthConfigJson("{\"workingDirectory\":\"" + workingDirectory.toString().replace("\\", "\\\\") + "\"}");

        String resultJson = executor.execute(tool, command("{\"query\":\"select\"}"));

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
        assertThat(runner.workingDirectory).isEqualTo(workingDirectory.normalize());
    }

    @Test
    void executeShouldRejectEndpointOrWorkingDirectoryOutsidePolicy() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        Path workingDirectory = Path.of("").toAbsolutePath();
        ToolConfig endpointDrift = cliTool("/usr/local/bin/other-report");
        endpointDrift.setAuthConfigJson("{\"workingDirectory\":\"" + workingDirectory.toString().replace("\\", "\\\\")
                + "\",\"_policy\":{\"allowedExecutable\":\"/usr/local/bin/report\",\"allowedWorkingDirectory\":\""
                + workingDirectory.toString().replace("\\", "\\\\") + "\"}}");

        assertThatThrownBy(() -> executor.execute(endpointDrift, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI endpoint is outside tool policy");

        ToolConfig directoryDrift = cliTool("/usr/local/bin/report");
        Path parentDirectory = workingDirectory.getParent() == null ? workingDirectory : workingDirectory.getParent();
        directoryDrift.setAuthConfigJson("{\"workingDirectory\":\"" + workingDirectory.toString().replace("\\", "\\\\")
                + "\",\"_policy\":{\"allowedExecutable\":\"/usr/local/bin/report\",\"allowedWorkingDirectory\":\""
                + parentDirectory.toString().replace("\\", "\\\\") + "\"}}");

        assertThatThrownBy(() -> executor.execute(directoryDrift, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI working directory is outside tool policy");
    }

    @Test
    void executeShouldRejectRelativeWorkingDirectory() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        tool.setAuthConfigJson("{\"workingDirectory\":\"tmp\"}");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI working directory must be absolute");
    }

    @Test
    void executeShouldRejectUnavailableWorkingDirectory() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{}", "")),
                Duration.ofSeconds(1),
                200
        );
        ToolConfig tool = cliTool("/usr/local/bin/report");
        Path unavailablePath = Path.of("").toAbsolutePath().resolve("missing-cli-workdir");
        tool.setAuthConfigJson("{\"workingDirectory\":\"" + unavailablePath.toString().replace("\\", "\\\\") + "\"}");

        assertThatThrownBy(() -> executor.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI working directory is not available");
    }

    @Test
    void executeShouldRedactSensitiveStdoutAndErrorDetail() {
        CliToolExecutor successExecutor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(0, "{\"token\":\"abc123\",\"rows\":1}", "")),
                Duration.ofSeconds(1),
                200
        );
        String resultJson = successExecutor.execute(cliTool("/usr/local/bin/report"), command("{}"));
        assertThat(resultJson).isEqualTo("{\"token\":\"***\",\"rows\":1}");

        CliToolExecutor failedExecutor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(2, "", "Bearer abc.def.ghi")),
                Duration.ofSeconds(1),
                200
        );
        assertThatThrownBy(() -> failedExecutor.execute(cliTool("/usr/local/bin/report"), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI tool failed with exit code 2: Bearer ***");
    }

    @Test
    void executeShouldReportNonZeroExitCode() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new CliProcessResult(2, "", "invalid request")),
                Duration.ofSeconds(1),
                200
        );

        assertThatThrownBy(() -> executor.execute(cliTool("/usr/local/bin/report"), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI tool failed with exit code 2: invalid request");
    }

    @Test
    void executeShouldReportTimeout() {
        CliToolExecutor executor = new CliToolExecutor(
                new CapturingRunner(new TimeoutException("timeout")),
                Duration.ofSeconds(1),
                200
        );

        assertThatThrownBy(() -> executor.execute(cliTool("/usr/local/bin/report"), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CLI tool timed out");
    }

    private ToolConfig cliTool(String endpointUrl) {
        ToolConfig tool = new ToolConfig();
        tool.setToolType("cli");
        tool.setToolCode("controlled.cli.query");
        tool.setEndpointUrl(endpointUrl);
        return tool;
    }

    private ExecuteToolCallCommand command(String payloadJson) {
        ExecuteToolCallCommand command = new ExecuteToolCallCommand();
        command.setCallPayloadJson(payloadJson);
        return command;
    }

    private static class CapturingRunner implements CliProcessRunner {

        private final CliProcessResult result;
        private final TimeoutException timeoutException;
        private List<String> command;
        private Path workingDirectory;
        private Duration timeout;
        private int maxOutputChars;

        CapturingRunner(CliProcessResult result) {
            this.result = result;
            this.timeoutException = null;
        }

        CapturingRunner(TimeoutException timeoutException) {
            this.result = null;
            this.timeoutException = timeoutException;
        }

        @Override
        public CliProcessResult run(List<String> command, Path workingDirectory, Duration timeout, int maxOutputChars)
                throws IOException, InterruptedException, TimeoutException {
            this.command = command;
            this.workingDirectory = workingDirectory;
            this.timeout = timeout;
            this.maxOutputChars = maxOutputChars;
            if (timeoutException != null) {
                throw timeoutException;
            }
            return result;
        }
    }
}
