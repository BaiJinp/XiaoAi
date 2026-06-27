package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 撤销命令
 */
@Component
public class UndoCommand implements CommandExecutor {

    @Override
public String getCommandName() {
        return "undo";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        // TODO: 实现撤销逻辑
        return SlashCommandService.CommandResult.success("Undo functionality not yet implemented");
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "undo",
                "Undo the last operation",
                "/undo"
        );
    }
}
