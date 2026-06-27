package com.xiaoai.agent.scheduled.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.scheduled.entity.ScheduledTask;

import java.util.List;

/**
 * 定时任务服务接口
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface ScheduledTaskService extends IService<ScheduledTask> {

    /**
     * 创建定时任务
     */
    ScheduledTask createTask(Long tenantId, String taskCode, String taskName,
                             String description, String cronExpression,
                             String naturalLanguage, String taskType,
                             Long agentId, String taskConfig,
                             String deliveryTargets, Long userId);

    /**
     * 根据代码获取任务
     */
    ScheduledTask getByCode(Long tenantId, String taskCode);

    /**
     * 获取用户的所有任务
     */
    List<ScheduledTask> getUserTasks(Long tenantId, Long userId);

    /**
     * 获取 Agent 的所有任务
     */
    List<ScheduledTask> getAgentTasks(Long tenantId, Long agentId);

    /**
     * 获取所有启用的任务
     */
    List<ScheduledTask> getEnabledTasks(Long tenantId);

    /**
     * 启用/禁用任务
     */
    void setEnabled(Long taskId, boolean enabled);

    /**
     * 暂停任务
     */
    void pauseTask(Long taskId);

    /**
     * 恢复任务
     */
    void resumeTask(Long taskId);

    /**
     * 立即执行任务
     */
    void executeNow(Long taskId);

    /**
     * 更新下次执行时间
     */
    void updateNextExecutionTime(Long taskId);

    /**
     * 记录执行结果
     */
    void recordExecution(Long taskId, boolean success);

    /**
     * 从自然语言创建任务
     */
    ScheduledTask createFromNaturalLanguage(Long tenantId, String naturalLanguage,
                                             String taskType, Long agentId,
                                             String taskConfig, Long userId);

    /**
     * 解析自然语言为 Cron 表达式
     */
    String parseNaturalLanguage(String naturalLanguage);

    /**
     * 删除任务
     */
    void deleteTask(Long taskId);
}
