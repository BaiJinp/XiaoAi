package com.xiaoai.agent.tool.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.EvaluateToolCallCommand;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import com.xiaoai.agent.tool.model.ToolCallDecisionResponse;
import com.xiaoai.agent.tool.model.ToolCallExecuteResponse;

public interface ToolConfigService extends IService<ToolConfig> {

    ToolConfig getToolConfig(Long toolId);

    ToolCallDecisionResponse evaluateToolCall(EvaluateToolCallCommand command);

    ToolCallExecuteResponse executeToolCall(ExecuteToolCallCommand command);
}
