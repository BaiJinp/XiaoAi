package com.xiaoai.agent.subagent.coordination.impl;

import com.xiaoai.agent.subagent.coordination.AggregatedResult;
import com.xiaoai.agent.subagent.coordination.ResultAggregationStrategy;
import com.xiaoai.agent.subagent.event.SubAgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全量合并聚合策略实现
 */
@Component
public class MergeAllAggregation implements ResultAggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(MergeAllAggregation.class);

    @Override
    public AggregatedResult aggregate(List<SubAgentEvent> results) {
        if (results == null || results.isEmpty()) {
            return AggregatedResult.builder()
                    .status("failed")
                    .summary("No results to merge")
                    .mergedContent("")
                    .successCount(0)
                    .failureCount(0)
                    .build();
        }

        // 合并所有内容
        String mergedContent = results.stream()
                .filter(event -> event.getPayload() != null)
                .map(SubAgentEvent::getPayload)
                .collect(Collectors.joining("\n\n---\n\n"));

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

        String summary = String.format("Merged %d results (%d successful, %d failed)",
                results.size(), successCount, failureCount);

        log.info("Merge all aggregation: status={}, total={}", status, results.size());

        return AggregatedResult.builder()
                .status(status)
                .summary(summary)
                .mergedContent(mergedContent)
                .successCount(successCount)
                .failureCount(failureCount)
                .build();
    }
}
