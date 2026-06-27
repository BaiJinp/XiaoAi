package com.xiaoai.agent.model.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 流式聊天响应的单个 chunk
 */
@Getter
@Builder
public class ChatModelChunk {

    /**
     * 本次 chunk 的文本内容
     */
    private final String content;

    /**
     * prompt token 数（通常只在最后一个 chunk 中有值）
     */
    private final Integer promptTokens;

    /**
     * completion token 数（通常只在最后一个 chunk 中有值）
     */
    private final Integer completionTokens;

    /**
     * 总 token 数
     */
    private final Integer totalTokens;

    /**
     * 是否是最后一个 chunk
     */
    @Builder.Default
    private final boolean done = false;

    /**
     * 停止原因（end_turn / max_tokens / tool_use / stop_sequence）
     */
    private final String stopReason;
}
