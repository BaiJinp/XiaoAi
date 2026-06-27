package com.xiaoai.agent.model.gateway.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Anthropic Messages API 响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicChatResponse {

    /**
     * 响应 ID
     */
    private String id;

    /**
     * 响应类型（"message"）
     */
    private String type;

    /**
     * 角色（"assistant"）
     */
    private String role;

    /**
     * 内容块列表
     */
    private List<ContentBlock> content;

    /**
     * 模型标识
     */
    private String model;

    /**
     * 停止原因（end_turn / max_tokens / stop_sequence / tool_use）
     */
    private String stopReason;

    /**
     * 停止序列（当 stop_reason=stop_sequence 时）
     */
    private String stopSequence;

    /**
     * Token 使用统计
     */
    private Usage usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlock {
        /**
         * 内容块类型（text / tool_use）
         */
        private String type;

        /**
         * 文本内容（type=text 时）
         */
        private String text;

        /**
         * 工具调用 ID（type=tool_use 时）
         */
        private String id;

        /**
         * 工具名称（type=tool_use 时）
         */
        private String name;

        /**
         * 工具输入参数（type=tool_use 时）
         */
        private Object input;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        /**
         * 输入 token 数
         */
        private Integer inputTokens;

        /**
         * 输出 token 数
         */
        private Integer outputTokens;
    }
}
