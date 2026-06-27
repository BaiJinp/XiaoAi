package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.springframework.stereotype.Component;

@Component
public class BuiltinEchoToolExecutor implements ToolExecutor {

    @Override
public boolean supports(ToolConfig tool) {
        return tool != null
                && "internal".equals(tool.getToolType())
                && "builtin.echo".equals(tool.getToolCode());
    }

    @Override
public String execute(ToolConfig tool, ExecuteToolCallCommand command) {
        return command.getCallPayloadJson() == null ? "{}" : command.getCallPayloadJson();
    }
}
