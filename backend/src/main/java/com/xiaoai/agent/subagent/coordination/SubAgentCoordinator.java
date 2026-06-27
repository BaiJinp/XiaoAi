package com.xiaoai.agent.subagent.coordination;

import com.xiaoai.agent.subagent.event.SubAgentEvent;
import com.xiaoai.agent.subagent.event.SubAgentEventStream;
import com.xiaoai.agent.subagent.message.SubAgentMessage;
import com.xiaoai.agent.subagent.message.SubAgentMessageBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 子代理协调器组件
 */
@Component
public class SubAgentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SubAgentCoordinator.class);

    private final SubAgentMessageBus messageBus;
    private final SubAgentEventStream eventStream;

    @Autowired
    public SubAgentCoordinator(SubAgentMessageBus messageBus, SubAgentEventStream eventStream) {
        this.messageBus = messageBus;
        this.eventStream = eventStream;
    }

    /**
     * 发送纠正消息给指定子代理
     */
    public void sendCorrection(String subAgentCode, String correction) {
        SubAgentMessage message = SubAgentMessage.builder()
                .messageCode(UUID.randomUUID().toString())
                .senderSubAgentCode("parent")
                .targetSubAgentCode(subAgentCode)
                .direction(SubAgentMessage.Direction.PARENT_TO_CHILD)
                .messageType(SubAgentMessage.MessageType.CORRECTION)
                .content(correction)
                .status(SubAgentMessage.Status.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        messageBus.send(subAgentCode, message);
        log.info("Sent correction to subAgentCode={}", subAgentCode);
    }

    /**
     * 广播消息给所有子代理
     */
    public void broadcastToChildren(String parentRunId, String content) {
        SubAgentMessage message = SubAgentMessage.builder()
                .messageCode(UUID.randomUUID().toString())
                .senderSubAgentCode("parent")
                .targetSubAgentCode(null)
                .direction(SubAgentMessage.Direction.PARENT_TO_CHILD)
                .messageType(SubAgentMessage.MessageType.TEXT)
                .content(content)
                .status(SubAgentMessage.Status.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        messageBus.send(null, message);
        log.info("Broadcast to children for parentRunId={}", parentRunId);
    }

    /**
     * 收集指定父运行ID的结果
     */
    public Map<String, SubAgentEvent> collectResults(String parentRunId, Duration timeout) {
        Map<String, SubAgentEvent> results = new HashMap<>();

        try {
            List<SubAgentEvent> events = eventStream.subscribe(parentRunId)
                    .filter(event -> event.getEventType() == SubAgentEvent.EventType.RESULT
                            || event.getEventType() == SubAgentEvent.EventType.ERROR)
                    .take(timeout)
                    .collectList()
                    .block();

            if (events != null) {
                for (SubAgentEvent event : events) {
                    results.put(event.getSubAgentCode(), event);
                }
            }
        } catch (Exception e) {
            log.error("Failed to collect results for parentRunId={}", parentRunId, e);
        }

        log.info("Collected {} results for parentRunId={}", results.size(), parentRunId);
        return results;
    }

    /**
     * 请求取消指定子代理的执行
     */
    public void requestCancellation(String subAgentCode) {
        SubAgentMessage message = SubAgentMessage.builder()
                .messageCode(UUID.randomUUID().toString())
                .senderSubAgentCode("parent")
                .targetSubAgentCode(subAgentCode)
                .direction(SubAgentMessage.Direction.PARENT_TO_CHILD)
                .messageType(SubAgentMessage.MessageType.CANCEL)
                .content("Cancellation requested")
                .status(SubAgentMessage.Status.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        messageBus.send(subAgentCode, message);
        log.info("Requested cancellation for subAgentCode={}", subAgentCode);
    }
}
