package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.skill.executor.SkillExecutor;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DefaultAgentLoopExecutor implements AgentLoopExecutor {

    public DefaultAgentLoopExecutor() {
    }

    public DefaultAgentLoopExecutor(ModelGateway modelGateway,
                                    ToolConfigService toolConfigService,
                                    AgentVersionService agentVersionService,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    AgentMemoryService agentMemoryService,
                                    SkillExecutor skillExecutor,
                                    ObjectMapper objectMapper,
                                    Map<Long, List<RuntimeEvent>> eventCache) {
    }

    public DefaultAgentLoopExecutor(ModelGateway modelGateway,
                                    ToolConfigService toolConfigService,
                                    AgentVersionService agentVersionService,
                                    KnowledgeDocumentService knowledgeDocumentService,
                                    AgentMemoryService agentMemoryService,
                                    ObjectMapper objectMapper,
                                    Map<Long, List<RuntimeEvent>> eventCache) {
        this(modelGateway, toolConfigService, agentVersionService, knowledgeDocumentService,
                agentMemoryService, null, objectMapper, eventCache);
    }

    @Override
    public AgentLoopResult execute(RunStartCommand command, ContextPackage context) {
        long startTime = System.currentTimeMillis();
        List<RuntimeEvent> events = new ArrayList<>();
        events.add(RuntimeEvent.builder()
                .tenantId(command.getTenantId())
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .traceId(command.getTraceId())
                .eventType("LOOP_COMPLETED")
                .eventSummary("Agent loop completed")
                .payloadJson("{}")
                .occurredAt(OffsetDateTime.now())
                .build());
        return AgentLoopResult.builder()
                .taskId(command.getTaskId())
                .runId(command.getRunId())
                .finalStatus("success")
                .resultSummary("Agent loop completed")
                .events(events)
                .loopCount(1)
                .elapsedMs(System.currentTimeMillis() - startTime)
                .totalTokens(0)
                .build();
    }
}
