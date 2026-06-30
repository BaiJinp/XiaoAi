package com.xiaoai.agent.runtime.engine;

import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agent 寰幆鎵ц鍣ㄦ帴鍙? * 瀹炵幇 observe 鈫?plan 鈫?act 鈫?reflect 寰幆锛岃妯″瀷椹卞姩鎵ц娴佺▼
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface AgentLoopExecutor {

    /**
     * 鎵ц Agent 寰幆
     *
     * @param command 杩愯鍚姩鍛戒护
     * @param context 涓婁笅鏂囧寘
     * @return 鎵ц缁撴灉
     */
    AgentLoopResult execute(RunStartCommand command, ContextPackage context);

    /**
     * Agent 寰幆鎵ц缁撴灉
     */
    @Getter
    @Builder
    class AgentLoopResult {
        private final Long taskId;
        private final Long runId;
        private final String finalStatus; // success, failed, cancelled, timeout
        private final String resultSummary;
        private final List<RuntimeEvent> events;
        private final int loopCount; // 瀹為檯寰幆娆℃暟
        private final long elapsedMs; // 执行耗时
        private final Integer totalTokens;
    }
}
