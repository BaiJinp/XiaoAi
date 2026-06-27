package com.xiaoai.agent.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.task.entity.TaskStep;

public interface TaskStepService extends IService<TaskStep> {

    TaskStep getStep(Long stepId);
}
