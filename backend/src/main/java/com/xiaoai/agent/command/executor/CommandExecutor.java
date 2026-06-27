package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;

import java.util.Map;

/**
 * 命令执行器接口
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface CommandExecutor {

    /**
     * 获取命令名称
     */
    String getCommandName();

    /**
     * 执行命令
     */
    SlashCommandService.CommandResult execute(Map<String, Object> params);

    /**
     * 获取命令信息
     */
    SlashCommandService.CommandInfo getCommandInfo();
}
