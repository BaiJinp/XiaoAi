package com.xiaoai.agent.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 定时任务实体
 */
@Getter
@Setter
@TableName("scheduled_task")
public class ScheduledTask extends TenantEntity {

    /**
     * 任务代码（唯一标识）
     */
    private String taskCode;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务描述
     */
    private String description;

    /**
     * Cron 表达式
     */
    private String cronExpression;

    /**
     * 自然语言描述（如"每天早上9点"）
     */
    private String naturalLanguage;

    /**
     * 任务类型（agent_task, workflow, notification, custom）
     */
    private String taskType;

    /**
     * 关联的 Agent ID
     */
    private Long agentId;

    /**
     * 任务配置（JSON）
     */
    private String taskConfig;

    /**
     * 投递目标（JSON数组，如[{"type":"telegram","chatId":"123"}]）
     */
    private String deliveryTargets;

    /**
     * 创建者用户ID
     */
    private Long createdByUserId;

    /**
     * 上次执行时间
     */
    private java.time.OffsetDateTime lastExecutionTime;

    /**
     * 下次执行时间
     */
    private java.time.OffsetDateTime nextExecutionTime;

    /**
     * 执行次数
     */
    private Integer executionCount;

    /**
     * 成功次数
     */
    private Integer successCount;

    /**
     * 失败次数
     */
    private Integer failureCount;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    /**
     * 状态（active, paused, error）
     */
    private String status;
}
