package com.xiaoai.agent.command;

import com.xiaoai.agent.command.executor.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斜杠命令服务
 * 管理和执行各种斜杠命令
 */
@Service
public class SlashCommandService {

    private final Map<String, CommandExecutor> commands = new HashMap<>();

    @Autowired
    public SlashCommandService(List<CommandExecutor> executors) {
        for (CommandExecutor executor : executors) {
            commands.put(executor.getCommandName(), executor);
        }
    }

    /**
     * 执行命令
     */
    public CommandResult executeCommand(String commandName, Map<String, Object> params) {
        CommandExecutor executor = commands.get(commandName);
        if (executor == null) {
            return CommandResult.error("Unknown command: " + commandName);
        }

        try {
            return executor.execute(params);
        } catch (Exception e) {
            return CommandResult.error("Command execution failed: " + e.getMessage());
        }
    }

    /**
     * 检查是否是斜杠命令
     */
    public boolean isSlashCommand(String input) {
        return input != null && input.startsWith("/");
    }

    /**
     * 解析命令和参数
     */
    public CommandParseResult parseCommand(String input) {
        if (!isSlashCommand(input)) {
            return null;
        }

        String[] parts = input.substring(1).split("\\s+", 2);
        String commandName = parts[0];
        Map<String, Object> params = new HashMap<>();

        if (parts.length > 1) {
            params.put("args", parts[1]);
        }

        return new CommandParseResult(commandName, params);
    }

    /**
     * 获取所有可用命令
     */
    public List<CommandInfo> getAllCommands() {
        return commands.values().stream()
                .map(CommandExecutor::getCommandInfo)
                .toList();
    }

    /**
     * 命令解析结果
     */
    public static class CommandParseResult {
        private final String commandName;
        private final Map<String, Object> params;

        public CommandParseResult(String commandName, Map<String, Object> params) {
            this.commandName = commandName;
            this.params = params;
        }
public String getCommandName() { return commandName; }
public Map<String, Object> getParams() { return params; }
    }

    /**
     * 命令结果
     */
    public static class CommandResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;

        public CommandResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static CommandResult success(String message) {
            return new CommandResult(true, message, null);
        }

        public static CommandResult success(String message, Map<String, Object> data) {
            return new CommandResult(true, message, data);
        }

        public static CommandResult error(String message) {
            return new CommandResult(false, message, null);
        }
public boolean isSuccess() { return success; }
public String getMessage() { return message; }
public Map<String, Object> getData() { return data; }
    }

    /**
     * 命令信息
     */
    public static class CommandInfo {
        private final String name;
        private final String description;
        private final String usage;

        public CommandInfo(String name, String description, String usage) {
            this.name = name;
            this.description = description;
            this.usage = usage;
        }
public String getName() { return name; }
public String getDescription() { return description; }
public String getUsage() { return usage; }
    }
}
