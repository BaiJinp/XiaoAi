package com.xiaoai.agent.runtime.engine;

public enum AgentLoopPhase {
    OBSERVE("observe"),
    PLAN("plan"),
    ACT("act"),
    REFLECT("reflect"),
    ARTIFACT("artifact");

    private final String code;

    AgentLoopPhase(String code) {
        this.code = code;
    }
public String getCode() {
        return code;
    }
}
