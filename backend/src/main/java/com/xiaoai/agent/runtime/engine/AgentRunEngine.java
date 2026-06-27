package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class AgentRunEngine {

    private final ContextPackageBuilder contextPackageBuilder;

    public AgentRunEngine(ObjectMapper objectMapper) {
        this(new ContextPackageBuilder(objectMapper));
    }

    @Autowired
    public AgentRunEngine(ContextPackageBuilder contextPackageBuilder) {
        this.contextPackageBuilder = contextPackageBuilder;
    }
public RunStartResult start(RunStartCommand command, Consumer<ContextPackage> executor) {
        ContextPackage contextPackage = buildContext(command);
        executor.accept(contextPackage);
        return RunStartResult.builder()
                .tenantId(command.getTenantId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .runtimeType("java-in-process")
                .accepted(true)
                .build();
    }
public ContextPackage buildContext(RunStartCommand command) {
        return contextPackageBuilder.build(command);
    }
}
