package com.xiaoai.agent.subagent.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子代理事件流管理器
 */
@Component
public class SubAgentEventStream {

    private static final Logger log = LoggerFactory.getLogger(SubAgentEventStream.class);

    private final Map<String, Sinks.Many<SubAgentEvent>> sinks = new ConcurrentHashMap<>();

    /**
     * 发布事件
     */
    public void publish(SubAgentEvent event) {
        if (event == null || event.getParentRunId() == null) {
            return;
        }

        String parentRunId = String.valueOf(event.getParentRunId());
        Sinks.Many<SubAgentEvent> sink = sinks.computeIfAbsent(parentRunId, k -> Sinks.many().multicast().onBackpressureBuffer());

        try {
            sink.tryEmitNext(event);
            log.debug("Published event: type={}, subAgentCode={}, parentRunId={}",
                    event.getEventType(), event.getSubAgentCode(), event.getParentRunId());
        } catch (Exception e) {
            log.error("Failed to publish event for parentRunId={}", parentRunId, e);
        }
    }

    /**
     * 订阅指定父运行ID的事件流
     */
    public Flux<SubAgentEvent> subscribe(String parentRunId) {
        Sinks.Many<SubAgentEvent> sink = sinks.computeIfAbsent(parentRunId, k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    /**
     * 完成指定父运行ID的事件流
     */
    public void complete(String parentRunId) {
        Sinks.Many<SubAgentEvent> sink = sinks.remove(parentRunId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Completed event stream for parentRunId={}", parentRunId);
        }
    }
}
