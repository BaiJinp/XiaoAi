package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 重试命令
 */
@Component
public class RetryCommand implements CommandExecutor {

    @Override
public String getCommandName() {
        return "retry";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        // TODO: 实现重试逻辑
        return SlashCommandService.CommandResult.success("Retry functionality not yet implemented");
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "retry",
                "Retry the last operation",
                "/retry"
        );
    }
}
