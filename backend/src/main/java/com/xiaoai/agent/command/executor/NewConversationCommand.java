package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import com.xiaoai.agent.conversation.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 新建对话命令
 */
@Component
public class NewConversationCommand implements CommandExecutor {

    private final ConversationService conversationService;

    @Autowired
    public NewConversationCommand(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
public String getCommandName() {
        return "new";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        Long tenantId = (Long) params.get("tenantId");
        Long userId = (Long) params.get("userId");
        Long agentId = (Long) params.get("agentId");
        String title = (String) params.getOrDefault("args", "New Conversation");

        var conversation = conversationService.createConversation(tenantId, userId, agentId, title);

        return SlashCommandService.CommandResult.success(
                "Created new conversation: " + conversation.getTitle(),
                Map.of("conversationId", conversation.getId())
        );
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "new",
                "Create a new conversation",
                "/new [title]"
        );
    }
}
