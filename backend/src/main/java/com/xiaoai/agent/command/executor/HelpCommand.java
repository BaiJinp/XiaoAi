package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 甯姪鍛戒护
 */
@Component
public class HelpCommand implements CommandExecutor {

    private final SlashCommandService slashCommandService;

    public HelpCommand(@Lazy SlashCommandService slashCommandService) {
        this.slashCommandService = slashCommandService;
    }

    @Override
public String getCommandName() {
        return "help";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        List<SlashCommandService.CommandInfo> commands = slashCommandService.getAllCommands();

        StringBuilder sb = new StringBuilder("Available commands:\n\n");
        for (var cmd : commands) {
            sb.append(String.format("/%-15s %s\n", cmd.getName(), cmd.getDescription()));
        }

        return SlashCommandService.CommandResult.success(sb.toString());
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "help",
                "Show available commands",
                "/help"
        );
    }
}
