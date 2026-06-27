package com.xiaoai.agent.subagent.coordination.impl;

import com.xiaoai.agent.subagent.coordination.AggregatedResult;
import com.xiaoai.agent.subagent.coordination.ResultAggregationStrategy;
import com.xiaoai.agent.subagent.event.SubAgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多数投票聚合策略实现
 */
@Component
public class MajorityVoteAggregation implements ResultAggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(MajorityVoteAggregation.class);

    @Override
    public AggregatedResult aggregate(List<SubAgentEvent> results) {
        if (results == null || results.isEmpty()) {
            return AggregatedResult.builder()
                    .status("failed")
                    .summary("No results to aggregate")
                    .mergedContent("")
                    .successCount(0)
                    .failureCount(0)
                    .build();
        }

        // 按内容分组并计数
        Map<String, Long> contentCounts = results.stream()
                .filter(event -> event.getPayload() != null)
                .collect(Collectors.groupingBy(
                        SubAgentEvent::getPayload,
                        Collectors.counting()
                ));

        // 找出出现次数最多的内容
        String majorityContent = contentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        long majorityCount = contentCounts.getOrDefault(majorityContent, 0L);
        int successCount = (int) results.stream()
                .filter(event -> event.getEventType() == SubAgentEvent.EventType.RESULT)
                .count();
        int failureCount = results.size() - successCount;

        String status;
        if (failureCount == 0) {
            status = "success";
        } else if (successCount > 0) {
            status = "partial";
        } else {
            status = "failed";
        }

        String summary = String.format("Majority vote: %d out of %d agreed on the same result",
                majorityCount, results.size());

        log.info("Majority vote aggregation: status={}, majorityCount={}, total={}",
                status, majorityCount, results.size());

        return AggregatedResult.builder()
                .status(status)
                .summary(summary)
                .mergedContent(majorityContent)
                .successCount(successCount)
                .failureCount(failureCount)
                .build();
    }
}
