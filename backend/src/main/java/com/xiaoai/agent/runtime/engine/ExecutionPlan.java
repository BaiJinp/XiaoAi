package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Agent 执行计划
 * 由模型在 plan 阶段生成，描述执行步骤和预期输出
 */
@Getter
@Setter
@Builder
public class ExecutionPlan {

    /**
     * 执行目标
     */
    private String goal;

    /**
     * 执行步骤列表
     */
    private List<ExecutionStep> steps;

    /**
     * 预期输出类型
     */
    @JsonProperty("outputType")
    private String outputType;

    /**
     * 执行步骤
     */
    @Getter
    @Setter
    @Builder
    public static class ExecutionStep {
        /**
         * 步骤ID
         */
        private String stepId;

        /**
         * 步骤类型：knowledge_retrieve, tool_call, model_call, user_input
         */
        private String stepType;

        /**
         * 步骤描述
         */
        private String description;

        /**
         * 工具ID（stepType=tool_call 时必填）
         */
        private Long toolId;

        /**
         * 工具调用参数JSON（stepType=tool_call 时必填）
         */
        private String callPayloadJson;

        /**
         * 知识库ID（stepType=knowledge_retrieve 时必填）
         */
        private Long knowledgeBaseId;

        /**
         * 检索查询（stepType=knowledge_retrieve 时必填）
         */
        private String query;

        /**
         * 检索数量（stepType=knowledge_retrieve 时可选）
         */
        private Integer topK;

        /**
         * 模型ID（stepType=model_call 时必填）
         */
        private Long modelId;

        /**
         * 模型提示词（stepType=model_call 时必填）
         */
        private String prompt;

        /**
         * 预期输出
         */
        private String expectedOutput;

        /**
         * 是否必须成功（失败时是否终止执行）
         */
        @Builder.Default
        private boolean required = true;
    }
}
