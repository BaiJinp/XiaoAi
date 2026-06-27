package com.xiaoai.agent.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.task.entity.TaskRun;

/**
 * TaskRun服务接口
 * <p>
 * 管理TaskRun（任务执行实例）的生命周期。
 * 一个Task可以有多个TaskRun（每次启动创建一个新的Run），
 * TaskRun记录执行状态、traceId、运行时快照和token用量等信息。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
public interface TaskRunService extends IService<TaskRun> {

    /**
     * 在当前租户上下文中根据ID获取TaskRun，不存在时抛出NOT_FOUND异常。
     *
     * @param runId TaskRun ID
     * @return TaskRun实体
     * @throws com.xiaoai.agent.common.exception.BusinessException 如果Run不存在
     */
    TaskRun getRun(Long runId);
}
