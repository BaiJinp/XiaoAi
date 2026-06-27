package com.xiaoai.agent.model.gateway.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Anthropic Messages API 请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicChatRequest {

    /**
     * 模型标识（如 claude-3-5-sonnet-20241022）
     */
    private String model;

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 最大输出 token 数
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 系统 prompt（可选）
     */
    private String system;

    /**
     * 温度参数（0-1）
     */
    private Double temperature;

    /**
     * top_p 参数
     */
    private Double topP;

    /**
     * top_k 参数
     */
    private Integer topK;

    /**
     * 停止序列
     */
    private List<String> stopSequences;

    /**
     * 工具定义（tool use）
     */
    private List<ToolDefinition> tools;

    /**
     * 工具选择策略
     */
    private ToolChoice toolChoice;

    /**
     * Extended thinking 配置
     */
    private ThinkingConfig thinking;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;  // "user" or "assistant"
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        private String name;
        private String description;
        private Object inputSchema;  // JSON Schema
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolChoice {
        private String type;  // "auto", "any", "tool"
        private String name;  // 当 type=tool 时指定工具名
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThinkingConfig {
        private String type;  // "enabled"
        private Integer budgetTokens;
    }
}
