package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPackageBuilderTest {

    private final ContextPackageBuilder contextPackageBuilder = new ContextPackageBuilder(new ObjectMapper());

    @Test
    void buildShouldParseInputAndRuntimeSnapshot() {
        ContextPackage contextPackage = contextPackageBuilder.build(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(99L)
                .channelType("web")
                .inputText("{\"prompt\":\"hello\"}")
                .runtimeSnapshotJson("{\"orchestrationPolicy\":{\"executionMode\":\"dynamic_workflow\"}}")
                .traceId("trace-1")
                .build());

        assertThat(contextPackage.getInputRoot().get("prompt").asText()).isEqualTo("hello");
        assertThat(contextPackage.getRuntimeSnapshotRoot().path("orchestrationPolicy").path("executionMode").asText())
                .isEqualTo("dynamic_workflow");
        assertThat(contextPackage.getExecutionMode()).isEqualTo(ExecutionMode.DYNAMIC_WORKFLOW);
    }

    @Test
    void buildShouldUseWrappedInputTextForPromptButKeepOriginalInputRoot() {
        ContextPackage contextPackage = contextPackageBuilder.build(RunStartCommand.builder()
                .tenantId(100L)
                .userId(200L)
                .agentId(10L)
                .agentVersionId(11L)
                .taskId(1L)
                .runId(99L)
                .channelType("collaboration")
                .inputText("{\"inputText\":\"clarify requirements\",\"collaborationContext\":{\"sessionId\":1}}")
                .runtimeSnapshotJson("{}")
                .traceId("trace-1")
                .build());

        assertThat(contextPackage.getInputText()).isEqualTo("clarify requirements");
        assertThat(contextPackage.getInputRoot().path("collaborationContext").path("sessionId").asLong()).isEqualTo(1L);
    }
}
