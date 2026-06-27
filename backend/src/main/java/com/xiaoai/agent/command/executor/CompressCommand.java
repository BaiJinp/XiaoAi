package com.xiaoai.agent.command.executor;

import com.xiaoai.agent.command.SlashCommandService;
import com.xiaoai.agent.conversation.compression.ConversationCompressionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 压缩对话命令
 */
@Component
public class CompressCommand implements CommandExecutor {

    private final ConversationCompressionService compressionService;

    @Autowired
    public CompressCommand(ConversationCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @Override
public String getCommandName() {
        return "compress";
    }

    @Override
    public SlashCommandService.CommandResult execute(Map<String, Object> params) {
        Long conversationId = (Long) params.get("conversationId");

        var result = compressionService.compressConversation(conversationId);

        if (result.isCompressed()) {
            return SlashCommandService.CommandResult.success(
                    String.format("Compressed conversation: saved %d tokens (%d -> %d)",
                            result.getTokensSaved(), result.getTokensBefore(), result.getTokensAfter()),
                    Map.of(
                            "tokensBefore", result.getTokensBefore(),
                            "tokensAfter", result.getTokensAfter(),
                            "tokensSaved", result.getTokensSaved()
                    )
            );
        } else {
            return SlashCommandService.CommandResult.success(result.getMessage());
        }
    }

    @Override
    public SlashCommandService.CommandInfo getCommandInfo() {
        return new SlashCommandService.CommandInfo(
                "compress",
                "Compress conversation to save tokens",
                "/compress"
        );
    }
}
