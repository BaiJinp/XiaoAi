package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import com.xiaoai.agent.personality.service.PersonalityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 切换人格命令
 */
@Component
public class PersonalityCommand implements CommandExecutor {

    private final PersonalityService personalityService;

    @Autowired
    public PersonalityCommand(PersonalityService personalityService) {
        this.personalityService = personalityService;
    }

    @Override
public String getCommandName() {
        return "personality";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        Long tenantId = (Long) params.get("tenantId");
        Long agentId = (Long) params.get("agentId");
        String args = (String) params.get("args");

        if (args == null || args.isEmpty()) {
            // 显示当前人格
            var personality = personalityService.getAgentPersonality(tenantId, agentId);
            if (personality != null) {
                return SlashCommandService.CommandResult.success(
                        "Current personality: " + personality.getPersonalityName(),
                        Map.of("personalityCode", personality.getPersonalityCode())
                );
            } else {
                return SlashCommandService.CommandResult.success("No personality set");
            }
        }

        // 设置新人格
        personalityService.setAgentPersonality(tenantId, agentId, args);
        return SlashCommandService.CommandResult.success("Personality set to: " + args);
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "personality",
                "View or set agent personality",
                "/personality [code]"
        );
    }
}
