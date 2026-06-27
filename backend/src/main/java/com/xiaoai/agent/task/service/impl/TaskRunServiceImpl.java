package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.mapper.TaskRunMapper;
import com.xiaoai.agent.task.service.TaskRunService;
import org.springframework.stereotype.Service;

/**
 * TaskRun服务实现类
 * <p>
 * 在当前租户上下文中提供TaskRun的查询功能。
 * TaskRun代表一次任务的执行实例，记录执行状态、traceId、
 * 运行时快照（snapshotJson）和token用量（usageJson）等信息。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class TaskRunServiceImpl extends ServiceImpl<TaskRunMapper, TaskRun> implements TaskRunService {

    /**
     * 在当前租户上下文中根据ID获取TaskRun。
     * 使用limit 1确保只返回一条记录，不存在时抛出NOT_FOUND异常。
     *
     * @param runId TaskRun ID
     * @return TaskRun实体
     * @throws BusinessException 如果Run不存在
     */
    @Override
public TaskRun getRun(Long runId) {
        Long tenantId = UserContextHolder.requireTenantId();
        TaskRun run = getBaseMapper().selectOne(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTenantId, tenantId)
                .eq(TaskRun::getId, runId)
                .last("limit 1"));
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task run not found");
        }
        return run;
    }
}
