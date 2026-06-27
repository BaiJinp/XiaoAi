package com.xiaoai.agent.subagent.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子代理消息总线组件
 */
@Component
public class SubAgentMessageBus {

    private static final Logger log = LoggerFactory.getLogger(SubAgentMessageBus.class);

    private final Map<String, List<SubAgentMessage>> messageQueues = new ConcurrentHashMap<>();

    /**
     * 发送消息
     */
    public void send(String targetSubAgentCode, SubAgentMessage message) {
        if (message == null) {
            return;
        }

        // 如果是广播，发送给所有队列
        if (targetSubAgentCode == null) {
            messageQueues.values().forEach(queue -> queue.add(message));
            log.debug("Broadcast message to all queues");
        } else {
            List<SubAgentMessage> queue = messageQueues.computeIfAbsent(targetSubAgentCode, k -> new CopyOnWriteArrayList<>());
            queue.add(message);
            log.debug("Sent message to subAgentCode={}: messageType={}", targetSubAgentCode, message.getMessageType());
        }
    }

    /**
     * 轮询指定子代理的消息
     */
    public List<SubAgentMessage> poll(String subAgentCode) {
        List<SubAgentMessage> queue = messageQueues.get(subAgentCode);
        if (queue == null || queue.isEmpty()) {
            return new ArrayList<>();
        }

        List<SubAgentMessage> messages = new ArrayList<>(queue);
        queue.clear();
        log.debug("Polled {} messages for subAgentCode={}", messages.size(), subAgentCode);
        return messages;
    }

    /**
     * 检查是否有待处理消息
     */
    public boolean hasPending(String subAgentCode) {
        List<SubAgentMessage> queue = messageQueues.get(subAgentCode);
        return queue != null && !queue.isEmpty();
    }
}
