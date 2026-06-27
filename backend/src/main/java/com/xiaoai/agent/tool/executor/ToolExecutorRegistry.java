package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.safety.SandboxExecutor;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.model.ExecuteToolCallCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorRegistry.class);

    private final List<ToolExecutor> executors;
    private final SandboxExecutor sandboxExecutor;

    public ToolExecutorRegistry(List<ToolExecutor> executors, SandboxExecutor sandboxExecutor) {
        this.executors = executors == null ? List.of() : List.copyOf(executors);
        this.sandboxExecutor = sandboxExecutor;
    }

    public static ToolExecutorRegistry defaultRegistry() {
        return new ToolExecutorRegistry(
                List.of(new BuiltinEchoToolExecutor(), new CliToolExecutor(), new HttpToolExecutor()),
                new SandboxExecutor()
        );
    }
public String execute(ToolConfig tool, ExecuteToolCallCommand command) {
        List<ToolExecutor> matchedExecutors = executors.stream()
                .filter(executor -> executor.supports(tool))
                .toList();
        if (matchedExecutors.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tool executor not implemented: " + (tool == null ? null : tool.getToolType()));
        }
        if (matchedExecutors.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Ambiguous tool executor: " + tool.getToolType() + "/" + tool.getToolCode());
        }

        // 使用沙箱执行工具
        if (sandboxExecutor != null) {
            return executeInSandbox(matchedExecutors.get(0), tool, command);
        }

        return matchedExecutors.get(0).execute(tool, command);
    }

    /**
     * 在沙箱中执行工具
     */
    private String executeInSandbox(ToolExecutor executor, ToolConfig tool, ExecuteToolCallCommand command) {
        Map<String, Object> context = new HashMap<>();
        context.put("toolCode", tool.getToolCode());
        context.put("toolType", tool.getToolType());
        context.put("riskLevel", tool.getRiskLevel());
        context.put("taskId", command.getTaskId());
        context.put("runId", command.getRunId());

        // 根据工具风险等级配置沙箱
        SandboxExecutor.SandboxConfig config = buildSandboxConfig(tool);

        log.info("Executing tool in sandbox: toolCode={}, riskLevel={}, timeoutMs={}",
                tool.getToolCode(), tool.getRiskLevel(), config.getTimeoutMs());

        SandboxExecutor.SandboxResult<String> result = sandboxExecutor.executeInSandbox(
                () -> executor.execute(tool, command),
                config,
                context
        );

        if (!result.isSuccess()) {
            String errorMessage = result.isTimeout()
                    ? "工具执行超时（" + config.getTimeoutMs() + "ms）: " + tool.getToolCode()
                    : "工具执行失败: " + result.getError();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, errorMessage);
        }

        log.info("Tool execution completed in sandbox: toolCode={}, executionTimeMs={}",
                tool.getToolCode(), result.getExecutionTimeMs());

        return result.getResult();
    }

    /**
     * 根据工具风险等级构建沙箱配置
     */
    private SandboxExecutor.SandboxConfig buildSandboxConfig(ToolConfig tool) {
        String riskLevel = tool.getRiskLevel();
        long timeoutMs;
        long maxMemoryMb;
        boolean allowNetworkAccess;
        boolean allowFileSystemAccess;

        if ("high".equalsIgnoreCase(riskLevel)) {
            // 高风险工具：严格限制
            timeoutMs = 10_000; // 10秒
            maxMemoryMb = 256;
            allowNetworkAccess = false;
            allowFileSystemAccess = false;
        } else if ("medium".equalsIgnoreCase(riskLevel)) {
            // 中等风险工具：适度限制
            timeoutMs = 30_000; // 30秒
            maxMemoryMb = 512;
            allowNetworkAccess = false;
            allowFileSystemAccess = false;
        } else {
            // 低风险工具：宽松限制
            timeoutMs = 60_000; // 60秒
            maxMemoryMb = 1024;
            allowNetworkAccess = true;
            allowFileSystemAccess = false;
        }

        return new SandboxExecutor.SandboxConfig(timeoutMs, maxMemoryMb, allowNetworkAccess, allowFileSystemAccess);
    }
}
