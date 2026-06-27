package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;

public interface ToolExecutor {

    boolean supports(ToolConfig tool);

    String execute(ToolConfig tool, ExecuteToolCallCommand command);
}
