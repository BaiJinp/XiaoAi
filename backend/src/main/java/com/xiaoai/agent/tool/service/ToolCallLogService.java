package com.xiaoai.agent.tool.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.tool.entity.ToolCallLog;

public interface ToolCallLogService extends IService<ToolCallLog> {

    ToolCallLog getToolCallLog(Long toolCallLogId);
}
