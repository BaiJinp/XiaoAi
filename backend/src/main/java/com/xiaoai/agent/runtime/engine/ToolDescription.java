package com.xiaoai.agent.runtime.engine;

import lombok.Builder;
import lombok.Getter;

/**
 * 工具描述
 * 用于向模型展示可用工具的详细信息
 */
@Getter
@Builder
public class ToolDescription {

    /**
     * 工具ID
     */
    private final Long toolId;

    /**
     * 工具代码（唯一标识）
     */
    private final String toolCode;

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 工具类型（builtin、cli、http）
     */
    private final String toolType;

    /**
     * 风险等级（low、medium、high）
     */
    private final String riskLevel;

    /**
     * 工具描述
     */
    private final String description;

    /**
     * 参数 schema（JSON Schema 格式）
     */
    private final String parametersSchema;

    /**
     * 使用示例
     */
    private final String usageExample;
}
