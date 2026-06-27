package com.xiaoai.agent.subagent.coordination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聚合结果（POJO）
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedResult {

    /**
     * 状态（success/partial/failed）
     */
    private String status;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 合并内容
     */
    private String mergedContent;

    /**
     * 成功数量
     */
    private int successCount;

    /**
     * 失败数量
     */
    private int failureCount;
}
