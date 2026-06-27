package com.xiaoai.agent.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.tool.entity.ToolCallLog;
import com.xiaoai.agent.tool.mapper.ToolCallLogMapper;
import com.xiaoai.agent.tool.service.ToolCallLogService;
import org.springframework.stereotype.Service;

@Service
public class ToolCallLogServiceImpl extends ServiceImpl<ToolCallLogMapper, ToolCallLog> implements ToolCallLogService {

    @Override
public ToolCallLog getToolCallLog(Long toolCallLogId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ToolCallLog log = getBaseMapper().selectOne(new LambdaQueryWrapper<ToolCallLog>()
                .eq(ToolCallLog::getTenantId, tenantId)
                .eq(ToolCallLog::getId, toolCallLogId)
                .last("limit 1"));
        if (log == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tool call log not found");
        }
        return log;
    }
}
