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

    private Long tenantId;

    private Long taskId;

    private Long runId;

    private Long stepId;

    @NotBlank
    private String prompt;

    /**
     * 鍝嶅簲鏍煎紡绾︽潫锛堢敤浜?Structured Output锛?     * 璁剧疆鍚庢ā鍨嬪皢寮哄埗杩斿洖绗﹀悎鎸囧畾鏍煎紡鐨勮緭鍑?     */
    private ResponseFormat responseFormat;
}
