package com.xiaoai.agent.subagent.coordination;

import com.xiaoai.agent.subagent.event.SubAgentEvent;

import java.util.List;

/**
 * 结果聚合策略接口
 */
public interface ResultAggregationStrategy {

    /**
     * 聚合多个子代理事件结果
     */
    AggregatedResult aggregate(List<SubAgentEvent> results);
}
