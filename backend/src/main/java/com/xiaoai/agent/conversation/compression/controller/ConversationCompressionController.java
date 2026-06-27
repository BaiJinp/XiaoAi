package com.xiaoai.agent.conversation.compression.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.conversation.compression.ConversationCompressionService;
import com.xiaoai.agent.conversation.compression.ConversationCompressionService.CompressionResult;
import com.xiaoai.agent.conversation.compression.ConversationCompressionService.CompressionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 对话压缩控制器
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/compression")
@CrossOrigin(origins = "*")
public class ConversationCompressionController {

    private final ConversationCompressionService compressionService;

    @Autowired
    public ConversationCompressionController(ConversationCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    /**
     * 获取压缩状态
     */
    @GetMapping("/status")
    public ApiResponse<CompressionStatus> getCompressionStatus(@PathVariable Long conversationId) {
        CompressionStatus status = compressionService.getCompressionStatus(conversationId);
        return ApiResponse.success(status);
    }

    /**
     * 手动压缩对话
     */
    @PostMapping("/compress")
    public ApiResponse<CompressionResult> compressConversation(@PathVariable Long conversationId) {
        CompressionResult result = compressionService.compressConversation(conversationId);
        return ApiResponse.success(result);
    }

    /**
     * 自动压缩（如果超过阈值）
     */
    @PostMapping("/auto-compress")
    public ApiResponse<CompressionResult> autoCompress(@PathVariable Long conversationId) {
        CompressionResult result = compressionService.autoCompress(conversationId);
        return ApiResponse.success(result);
    }

    /**
     * 计算当前 token 使用量
     */
    @GetMapping("/tokens")
    public ApiResponse<Long> calculateTokens(@PathVariable Long conversationId) {
        long tokens = compressionService.calculateCurrentTokens(conversationId);
        return ApiResponse.success(tokens);
    }
}
