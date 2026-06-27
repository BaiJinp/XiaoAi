package com.xiaoai.agent.model.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 模型响应格式约束
 * 用于 Structured Output 场景，强制模型返回符合指定 JSON Schema 的输出。
 */
@Getter
@Builder
public class ResponseFormat {

    /**
     * 格式类型：text / json_object / json_schema
     */
    private final String type;

    /**
     * JSON Schema 名称（仅 json_schema 类型使用）
     */
    private final String schemaName;

    /**
     * JSON Schema 定义（仅 json_schema 类型使用）
     */
    private final String schema;

    /**
     * 是否严格模式（仅 json_schema 类型使用）
     */
    @Builder.Default
    private final boolean strict = true;

    /**
     * 创建 json_object 格式
     */
    public static ResponseFormat jsonObject() {
        return ResponseFormat.builder().type("json_object").build();
    }

    /**
     * 创建 json_schema 格式
     */
    public static ResponseFormat jsonSchema(String name, String schema) {
        return ResponseFormat.builder()
                .type("json_schema")
                .schemaName(name)
                .schema(schema)
                .strict(true)
                .build();
    }

    /**
     * 创建纯文本格式
     */
    public static ResponseFormat text() {
        return ResponseFormat.builder().type("text").build();
    }
}
