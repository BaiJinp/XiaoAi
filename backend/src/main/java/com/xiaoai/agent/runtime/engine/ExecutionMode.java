package com.xiaoai.agent.runtime.engine;

import java.util.Arrays;

public enum ExecutionMode {
    DIRECT_TOOL("direct_tool"),
    SINGLE_AGENT("single_agent"),
    DYNAMIC_WORKFLOW("dynamic_workflow"),
    MULTI_AGENT("multi_agent"),
    AGENT_LOOP("agent_loop");

    private final String code;

    ExecutionMode(String code) {
        this.code = code;
    }
public String getCode() {
        return code;
    }

    public static ExecutionMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst()
                .orElse(SINGLE_AGENT);
    }
}
