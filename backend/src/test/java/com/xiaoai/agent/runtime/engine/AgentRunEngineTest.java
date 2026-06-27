package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunEngineTest {

    private final AgentRunEngine agentRunEngine = new AgentRunEngine(new ObjectMapper());

    @Test
    void buildContextShouldUseExplicitExecutionModeFromInput() {
        ContextPackage contextPackage = agentRunEngine.buildContext(command("{\"executionMode\":\"multi_agent\"}", null));

        assertThat(contextPackage.getExecutionMode()).isEqualTo(ExecutionMode.MULTI_AGENT);
    }

    @Test
    void buildContextShouldUseExecutionModeFromRuntimeSnapshot() {
        ContextPackage contextPackage = agentRunEngine.buildContext(command(
                "{\"prompt\":\"hello\"}",
                "{\"orchestrationPolicy\":{\"executionMode\":\"dynamic_workflow\"}}"
        ));

        assertThat(contextPackage.getExecutionMode()).isEqualTo(ExecutionMode.DYNAMIC_WORKFLOW);
    }

    @Test
    void buildContextShouldInferDirectToolModeForToolOnlyInput() {
        ContextPackage contextPackage = agentRunEngine.buildContext(command("{\"toolId\":10}", null));

        assertThat(contextPackage.getExecutionMode()).isEqualTo(ExecutionMode.DIRECT_TOOL);
    }

    @Test
    void buildContextShouldInferSingleAgentModeByDefault() {
        ContextPackage contextPackage = agentRunEngine.buildContext(command("{\"prompt\":\"hello\"}", null));

        assertThat(contextPackage.getExecutionMode()).isEqualTo(ExecutionMode.SINGLE_AGENT);
    }

    @Test
    void startShouldBuildContextAndReturnAcceptedResult() {
        AtomicReference<ContextPackage> captured = new AtomicReference<>();

        var result = agentRunEngine.start(command("{\"toolId\":10}", null), captured::set);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getRuntimeType()).isEqualTo("java-in-process");
        assertThat(captured.get().getExecutionMode()).isEqualTo(ExecutionMode.DIRECT_TOOL);
        assertThat(captured.get().getTenantId()).isEqualTo(100L);
        assertThat(captured.get().getRunId()).isEqualTo(99L);
    }

    private RunStartCommand command(String inputText, String runtimeSnapshotJson) {
        return RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(99L)
                .channelType("web")
                .inputText(inputText)
                .runtimeSnapshotJson(runtimeSnapshotJson)
                .traceId("trace-1")
                .build();
    }
}
