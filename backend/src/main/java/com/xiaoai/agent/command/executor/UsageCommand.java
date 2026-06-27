package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import com.xiaoai.agent.conversation.compression.ConversationCompressionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 查看使用情况命令
 */
@Component
public class UsageCommand implements CommandExecutor {

    private final ConversationCompressionService compressionService;

    @Autowired
    public UsageCommand(ConversationCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @Override
public String getCommandName() {
        return "usage";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        Long conversationId = (Long) params.get("conversationId");

        long tokens = compressionService.calculateCurrentTokens(conversationId);
        var status = compressionService.getCompressionStatus(conversationId);

        String message = String.format(
                "Token usage: %d / %d (%.1f%%)\nCompressed messages: %d / %d",
                tokens, status.getTokenLimit(), status.getUsagePercent(),
                status.getCompressedMessages(), status.getTotalMessages()
        );

        return SlashCommandService.CommandResult.success(
                message,
                Map.of(
                        "currentTokens", tokens,
                        "tokenLimit", status.getTokenLimit(),
                        "usagePercent", status.getUsagePercent(),
                        "compressedMessages", status.getCompressedMessages(),
                        "totalMessages", status.getTotalMessages()
                )
        );
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "usage",
                "Show token usage statistics",
                "/usage"
        );
    }
}
