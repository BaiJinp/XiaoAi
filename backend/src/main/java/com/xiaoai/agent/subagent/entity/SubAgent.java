package com.xiaoai.agent.subagent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 子代理实体
 */
@Getter
@Setter
@TableName("sub_agent")
public class SubAgent extends TenantEntity {

    /**
     * 子代理代码（唯一标识）
     */
    private String subAgentCode;

    /**
     * 子代理名称
     */
    private String subAgentName;

    /**
     * 父任务ID
     */
    private Long parentTaskId;

    /**
     * 父运行ID
     */
    private Long parentRunId;

    /**
     * 关联的 Agent ID
     */
    private Long agentId;

    /**
     * 关联的 Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 子代理任务
     */
    private String taskDescription;

    /**
     * 子代理状态（pending, running, completed, failed, cancelled）
     */
    private String status;

    /**
     * 执行结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 开始时间
     */
    private java.time.OffsetDateTime startTime;

    /**
     * 结束时间
     */
    private java.time.OffsetDateTime endTime;

    /**
     * Token 使用量
     */
    private Integer tokenUsage;

    /**
     * 优先级（数字越小优先级越高）
     */
    private Integer priority;

    /**
     * 是否隔离执行
     */
    private Boolean isolated;

    /**
     * 超时时间（毫秒）
     */
    private Long timeoutMs;
}
