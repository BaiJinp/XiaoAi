package com.xiaoai.agent.runtime.engine;

import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agent 循环执行器接口
 * 实现 observe → plan → act → reflect 循环，让模型驱动执行流程
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface AgentLoopExecutor {

    /**
     * 执行 Agent 循环
     *
     * @param command 运行启动命令
     * @param context 上下文包
     * @return 执行结果
     */
    AgentLoopResult execute(RunStartCommand command, ContextPackage context);

    /**
     * Agent 循环执行结果
     */
    @Getter
    @Builder
    class AgentLoopResult {
        private final Long taskId;
        private final Long runId;
        private final String finalStatus; // success, failed, cancelled, timeout
        private final String resultSummary;
        private final List<RuntimeEvent> events;
        private final int loopCount; // 实际循环次数
        private final long elapsedMs; // 执行耗时
    }
}
