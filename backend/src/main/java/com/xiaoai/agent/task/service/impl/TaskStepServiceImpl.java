package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskStep;
import com.xiaoai.agent.task.mapper.TaskStepMapper;
import com.xiaoai.agent.task.service.TaskStepService;
import org.springframework.stereotype.Service;

@Service
public class TaskStepServiceImpl extends ServiceImpl<TaskStepMapper, TaskStep> implements TaskStepService {

    @Override
public TaskStep getStep(Long stepId) {
        Long tenantId = UserContextHolder.requireTenantId();
        TaskStep step = getBaseMapper().selectOne(new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTenantId, tenantId)
                .eq(TaskStep::getId, stepId)
                .last("limit 1"));
        if (step == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task step not found");
        }
        return step;
    }
}
