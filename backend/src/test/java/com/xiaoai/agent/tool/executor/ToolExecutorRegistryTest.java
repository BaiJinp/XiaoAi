package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutorRegistryTest {

    @Test
    void executeShouldDispatchToMatchedExecutor() {
        ToolExecutorRegistry registry = new ToolExecutorRegistry(List.of(new BuiltinEchoToolExecutor()));
        ToolConfig tool = tool("internal", "builtin.echo");
        ExecuteToolCallCommand command = command("{\"ok\":true}");

        String resultJson = registry.execute(tool, command);

        assertThat(resultJson).isEqualTo("{\"ok\":true}");
    }

    @Test
    void executeShouldRejectMissingExecutorWithExistingMessageContract() {
        ToolExecutorRegistry registry = new ToolExecutorRegistry(List.of(new BuiltinEchoToolExecutor()));
        ToolConfig tool = tool("http", "http.query");

        assertThatThrownBy(() -> registry.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tool executor not implemented: http");
    }

    @Test
    void defaultRegistryShouldIncludeHttpExecutor() {
        ToolExecutorRegistry registry = ToolExecutorRegistry.defaultRegistry();
        ToolConfig tool = tool("http", "controlled.http.query");
        tool.setEndpointUrl("http://127.0.0.1:8080/query");

        assertThatThrownBy(() -> registry.execute(tool, command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("HTTP endpoint host is not allowed");
    }

    @Test
    void executeShouldRejectAmbiguousExecutor() {
        ToolExecutor first = new AlwaysMatchedToolExecutor("first");
        ToolExecutor second = new AlwaysMatchedToolExecutor("second");
        ToolExecutorRegistry registry = new ToolExecutorRegistry(List.of(first, second));

        assertThatThrownBy(() -> registry.execute(tool("internal", "builtin.echo"), command("{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ambiguous tool executor: internal/builtin.echo");
    }

    private ToolConfig tool(String toolType, String toolCode) {
        ToolConfig tool = new ToolConfig();
        tool.setToolType(toolType);
        tool.setToolCode(toolCode);
        return tool;
    }

    private ExecuteToolCallCommand command(String payloadJson) {
        ExecuteToolCallCommand command = new ExecuteToolCallCommand();
        command.setCallPayloadJson(payloadJson);
        return command;
    }

    private record AlwaysMatchedToolExecutor(String resultJson) implements ToolExecutor {

        @Override
        public boolean supports(ToolConfig tool) {
            return true;
        }

        @Override
        public String execute(ToolConfig tool, ExecuteToolCallCommand command) {
            return resultJson;
        }
    }
}
