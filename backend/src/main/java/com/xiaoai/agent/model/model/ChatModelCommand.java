package com.xiaoai.agent.model.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatModelCommand {

    @NotNull
    private Long modelId;

    private Long taskId;

    private Long runId;

    private Long stepId;

    @NotBlank
    private String prompt;

    /**
     * 响应格式约束（用于 Structured Output）
     * 设置后模型将强制返回符合指定格式的输出
     */
    private ResponseFormat responseFormat;
}
