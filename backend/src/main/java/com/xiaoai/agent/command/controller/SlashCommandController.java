package com.xiaoai.agent.command.controller;

import com.xiaoai.agent.command.SlashCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 斜杠命令控制器
 */
@RestController
@RequestMapping("/api/v1/commands")
@CrossOrigin(origins = "*")
public class SlashCommandController {

    private final SlashCommandService slashCommandService;

    @Autowired
    public SlashCommandController(SlashCommandService slashCommandService) {
        this.slashCommandService = slashCommandService;
    }

    /**
     * 执行斜杠命令
     */
    @PostMapping("/execute")
    public SlashCommandService.CommandResult executeCommand(@RequestBody Map<String, Object> request) {
        String input = (String) request.get("input");

        if (!slashCommandService.isSlashCommand(input)) {
            return SlashCommandService.CommandResult.error("Not a slash command");
        }

        SlashCommandService.CommandParseResult parsed = slashCommandService.parseCommand(input);
        if (parsed == null) {
            return SlashCommandService.CommandResult.error("Failed to parse command");
        }

        // 添加上下文参数
        Map<String, Object> params = parsed.getParams();
        params.put("tenantId", request.get("tenantId"));
        params.put("userId", request.get("userId"));
        params.put("agentId", request.get("agentId"));
        params.put("conversationId", request.get("conversationId"));

        return slashCommandService.executeCommand(parsed.getCommandName(), params);
    }

    /**
     * 获取所有可用命令
     */
    @GetMapping
    public List<SlashCommandService.CommandInfo> getAllCommands() {
        return slashCommandService.getAllCommands();
    }
}
