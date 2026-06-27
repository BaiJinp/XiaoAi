package com.xiaoai.agent.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.model.entity.ModelCallLog;
import com.xiaoai.agent.model.mapper.ModelCallLogMapper;
import com.xiaoai.agent.model.service.ModelCallLogService;
import org.springframework.stereotype.Service;

@Service
public class ModelCallLogServiceImpl extends ServiceImpl<ModelCallLogMapper, ModelCallLog> implements ModelCallLogService {

    @Override
public ModelCallLog getModelCallLog(Long modelCallLogId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ModelCallLog log = getBaseMapper().selectOne(new LambdaQueryWrapper<ModelCallLog>()
                .eq(ModelCallLog::getTenantId, tenantId)
                .eq(ModelCallLog::getId, modelCallLogId)
                .last("limit 1"));
        if (log == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Model call log not found");
        }
        return log;
    }
}
