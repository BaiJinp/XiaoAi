package com.xiaoai.agent.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.model.entity.ModelCallLog;

public interface ModelCallLogService extends IService<ModelCallLog> {

    ModelCallLog getModelCallLog(Long modelCallLogId);
}
