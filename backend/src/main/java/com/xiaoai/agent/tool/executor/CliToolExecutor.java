package com.xiaoai.agent.tool.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.List;

@Component
public class CliToolExecutor implements ToolExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_OUTPUT_CHARS = 4000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> BLOCKED_SHELL_EXECUTABLES = Set.of(
            "cmd",
            "cmd.exe",
            "powershell",
            "powershell.exe",
            "pwsh",
            "pwsh.exe",
            "bash",
            "sh"
    );
    private final CliProcessRunner processRunner;
    private final Duration timeout;
    private final int maxOutputChars;
    private final JsonSchemaPayloadValidator payloadValidator = new JsonSchemaPayloadValidator();

    public CliToolExecutor() {
        this(new DefaultCliProcessRunner(), DEFAULT_TIMEOUT, MAX_OUTPUT_CHARS);
    }

    CliToolExecutor(CliProcessRunner processRunner, Duration timeout, int maxOutputChars) {
        this.processRunner = processRunner;
        this.timeout = timeout;
        this.maxOutputChars = maxOutputChars;
    }

    @Override
public boolean supports(ToolConfig tool) {
        return tool != null && "cli".equals(tool.getToolType());
    }

    @Override
public String execute(ToolConfig tool, ExecuteToolCallCommand command) {
        validateToolCode(tool.getToolCode());
        String executable = normalizeExecutable(tool.getEndpointUrl());
        String payloadJson = command.getCallPayloadJson() == null ? "{}" : command.getCallPayloadJson();
        JsonNode payload = parseJson(payloadJson, "CLI payload is invalid JSON");
        validatePayloadSchema(tool.getSchemaJson(), payload);
        JsonNode authConfig = parseJson(tool.getAuthConfigJson() == null || tool.getAuthConfigJson().isBlank() ? "{}" : tool.getAuthConfigJson(),
                "CLI auth config is invalid JSON");
        Path workingDirectory = parseWorkingDirectory(authConfig);
        validateExecutionPolicy(authConfig, executable, workingDirectory);
        try {
            CliProcessResult result = processRunner.run(List.of(executable, payloadJson), workingDirectory, timeout, maxOutputChars);
            if (result.exitCode() != 0) {
                String detail = ToolOutputRedactor.redact(firstNonBlank(result.stderr(), result.stdout()));
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "CLI tool failed with exit code " + result.exitCode() + formatDetail(detail));
            }
            return result.stdout() == null || result.stdout().isBlank()
                    ? "{\"exitCode\":0}"
                    : ToolOutputRedactor.redact(result.stdout());
        } catch (TimeoutException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI tool timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI tool execution interrupted");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI tool execution failed: " + ex.getMessage());
        }
    }

    private void validateToolCode(String toolCode) {
        if (toolCode == null || !toolCode.startsWith("controlled.cli.")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI tool code is not allowed");
        }
    }

    private String normalizeExecutable(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI endpoint is required");
        }
        String executable = endpointUrl.trim();
        if (executable.contains("\n") || executable.contains("\r")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI endpoint is invalid");
        }
        if (executable.length() >= 2 && executable.startsWith("\"") && executable.endsWith("\"")) {
            executable = executable.substring(1, executable.length() - 1);
        }
        String fileName = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT);
        if (BLOCKED_SHELL_EXECUTABLES.contains(fileName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Shell execution is not allowed for CLI tools");
        }
        return executable;
    }

    private void validateExecutionPolicy(JsonNode config, String executable, Path workingDirectory) {
        JsonNode policy = config.path("_policy");
        if (!policy.isObject()) {
            return;
        }
        JsonNode allowedExecutable = policy.path("allowedExecutable");
        if (allowedExecutable.isTextual() && !allowedExecutable.asText().isBlank()
                && !Path.of(allowedExecutable.asText()).normalize().equals(Path.of(executable).normalize())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI endpoint is outside tool policy");
        }
        JsonNode allowedWorkingDirectory = policy.path("allowedWorkingDirectory");
        if (allowedWorkingDirectory.isTextual() && !allowedWorkingDirectory.asText().isBlank()) {
            Path allowedDirectory = Path.of(allowedWorkingDirectory.asText()).normalize();
            if (workingDirectory == null || !allowedDirectory.equals(workingDirectory.normalize())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI working directory is outside tool policy");
            }
        }
    }

    private JsonNode parseJson(String json, String errorMessage) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return node == null || node.isMissingNode() ? OBJECT_MAPPER.createObjectNode() : node;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, errorMessage);
        }
    }

    private void validatePayloadSchema(String schemaJson, JsonNode payload) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return;
        }
        JsonNode schema = parseJson(schemaJson, "CLI schema is invalid JSON");
        payloadValidator.validatePayloadSchema(schema, payload, "CLI payload");
    }

    private Path parseWorkingDirectory(JsonNode config) {
        if (config == null || config.isMissingNode() || config.isNull()) {
            return null;
        }
        JsonNode workingDirectory = config.path("workingDirectory");
        if (!workingDirectory.isTextual() || workingDirectory.asText().isBlank()) {
            return null;
        }
        Path path = Path.of(workingDirectory.asText()).normalize();
        if (!path.isAbsolute()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI working directory must be absolute");
        }
        if (!path.toFile().isDirectory()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "CLI working directory is not available");
        }
        return path;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String formatDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        return ": " + truncate(detail.trim(), 500);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
