package com.xiaoai.agent.subagent.executor;

import com.xiaoai.agent.runtime.engine.AgentLoopExecutor;
import com.xiaoai.agent.runtime.engine.ContextPackage;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.subagent.entity.SubAgent;
import com.xiaoai.agent.subagent.service.SubAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 子代理并行执行引擎
 */
@Component
public class SubAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(SubAgentExecutor.class);

    private final SubAgentService subAgentService;
    private final AgentLoopExecutor agentLoopExecutor;
    private final ExecutorService executorService;

    @Autowired
    public SubAgentExecutor(SubAgentService subAgentService,
                           AgentLoopExecutor agentLoopExecutor) {
        this.subAgentService = subAgentService;
        this.agentLoopExecutor = agentLoopExecutor;
        // 创建固定大小的线程池用于并行执行
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * 并行执行多个子代理
     */
    public ParallelExecutionResult executeParallel(Long parentTaskId, List<SubAgent> subAgents) {
        log.info("Executing {} sub-agents in parallel for parent task {}", subAgents.size(), parentTaskId);

        List<Future<SubAgentResult>> futures = new ArrayList<>();

        // 提交所有子代理任务
        for (SubAgent subAgent : subAgents) {
            Future<SubAgentResult> future = executorService.submit(() -> executeSubAgent(subAgent));
            futures.add(future);
        }

        // 等待所有任务完成
        List<SubAgentResult> results = new ArrayList<>();
        for (Future<SubAgentResult> future : futures) {
            try {
                SubAgentResult result = future.get(5, TimeUnit.MINUTES);
                results.add(result);
            } catch (TimeoutException e) {
                log.error("Sub-agent execution timeout");
                results.add(SubAgentResult.timeout());
            } catch (Exception e) {
                log.error("Sub-agent execution failed", e);
                results.add(SubAgentResult.error(e.getMessage()));
            }
        }

        // 聚合结果
        return aggregateResults(results);
    }

    /**
     * 执行单个子代理
     */
    private SubAgentResult executeSubAgent(SubAgent subAgent) {
        log.info("Executing sub-agent: {} - {}", subAgent.getSubAgentCode(), subAgent.getTaskDescription());

        try {
            // 更新状态为运行中
            subAgentService.updateStatus(subAgent.getId(), "running");

            // 构建执行命令
            RunStartCommand command = RunStartCommand.builder()
                    .tenantId(subAgent.getTenantId())
                    .agentId(subAgent.getAgentId())
                    .agentVersionId(subAgent.getAgentVersionId())
                    .taskId(subAgent.getParentTaskId())
                    .runId(subAgent.getParentRunId())
                    .inputText("{\"subAgentTask\":\"" + subAgent.getTaskDescription() + "\"}")
                    .build();

            // 构建上下文
            ContextPackage context = ContextPackage.builder()
                    .tenantId(subAgent.getTenantId())
                    .agentId(subAgent.getAgentId())
                    .agentVersionId(subAgent.getAgentVersionId())
                    .inputText(subAgent.getTaskDescription())
                    .build();

            // 执行 Agent 循环
            AgentLoopExecutor.AgentLoopResult loopResult = agentLoopExecutor.execute(command, context);

            // 设置结果
            String result = loopResult.getResultSummary();
            Integer tokenUsage = loopResult.getTotalTokens();

            subAgentService.setResult(subAgent.getId(), result, tokenUsage);

            log.info("Sub-agent completed: {} - tokens: {}", subAgent.getSubAgentCode(), tokenUsage);

            return SubAgentResult.success(result, tokenUsage);

        } catch (Exception e) {
            log.error("Sub-agent execution failed: {}", subAgent.getSubAgentCode(), e);
            subAgentService.setError(subAgent.getId(), e.getMessage());
            return SubAgentResult.error(e.getMessage());
        }
    }

    /**
     * 聚合并行执行结果
     */
    private ParallelExecutionResult aggregateResults(List<SubAgentResult> results) {
        int totalTokens = 0;
        int successCount = 0;
        int failureCount = 0;
        StringBuilder combinedResult = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            SubAgentResult result = results.get(i);

            if (result.isSuccess()) {
                successCount++;
                totalTokens += result.getTokenUsage();
                combinedResult.append("## Task ").append(i + 1).append("\n");
                combinedResult.append(result.getResult()).append("\n\n");
            } else {
                failureCount++;
                combinedResult.append("## Task ").append(i + 1).append(" (Failed)\n");
                combinedResult.append("Error: ").append(result.getError()).append("\n\n");
            }
        }

        return new ParallelExecutionResult(
                successCount,
                failureCount,
                totalTokens,
                combinedResult.toString()
        );
    }

    /**
     * 子代理执行结果
     */
    public static class SubAgentResult {
        private final boolean success;
        private final String result;
        private final String error;
        private final Integer tokenUsage;

        private SubAgentResult(boolean success, String result, String error, Integer tokenUsage) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.tokenUsage = tokenUsage;
        }

        public static SubAgentResult success(String result, Integer tokenUsage) {
            return new SubAgentResult(true, result, null, tokenUsage);
        }

        public static SubAgentResult error(String error) {
            return new SubAgentResult(false, null, error, 0);
        }

        public static SubAgentResult timeout() {
            return new SubAgentResult(false, null, "Execution timeout", 0);
        }
public boolean isSuccess() { return success; }
public String getResult() { return result; }
public String getError() { return error; }
public Integer getTokenUsage() { return tokenUsage; }
    }

    /**
     * 并行执行结果
     */
    public static class ParallelExecutionResult {
        private final int successCount;
        private final int failureCount;
        private final int totalTokens;
        private final String combinedResult;

        public ParallelExecutionResult(int successCount, int failureCount,
                                      int totalTokens, String combinedResult) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalTokens = totalTokens;
            this.combinedResult = combinedResult;
        }
public int getSuccessCount() { return successCount; }
public int getFailureCount() { return failureCount; }
public int getTotalTokens() { return totalTokens; }
public String getCombinedResult() { return combinedResult; }
public boolean isAllSuccess() { return failureCount == 0; }
    }

    /**
     * 关闭执行引擎
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
