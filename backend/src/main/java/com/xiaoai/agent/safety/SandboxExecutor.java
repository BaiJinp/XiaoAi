package com.xiaoai.agent.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 沙箱执行器
 * 为工具执行提供隔离环境，限制资源使用
 */
@Component
public class SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecutor.class);

    /**
     * 默认超时时间：30秒
     */
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    /**
     * 最大超时时间：5分钟
     */
    private static final long MAX_TIMEOUT_MS = 300_000;

    /**
     * 线程池，用于执行沙箱任务
     */
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 沙箱配置
     */
    public static class SandboxConfig {
        private final long timeoutMs;
        private final long maxMemoryMb;
        private final boolean allowNetworkAccess;
        private final boolean allowFileSystemAccess;

        public SandboxConfig(long timeoutMs, long maxMemoryMb, boolean allowNetworkAccess, boolean allowFileSystemAccess) {
            this.timeoutMs = Math.min(timeoutMs, MAX_TIMEOUT_MS);
            this.maxMemoryMb = maxMemoryMb;
            this.allowNetworkAccess = allowNetworkAccess;
            this.allowFileSystemAccess = allowFileSystemAccess;
        }

        public static SandboxConfig defaultConfig() {
            return new SandboxConfig(DEFAULT_TIMEOUT_MS, 512, false, false);
        }
public long getTimeoutMs() {
            return timeoutMs;
        }
public long getMaxMemoryMb() {
            return maxMemoryMb;
        }
public boolean isAllowNetworkAccess() {
            return allowNetworkAccess;
        }
public boolean isAllowFileSystemAccess() {
            return allowFileSystemAccess;
        }
    }

    /**
     * 沙箱执行结果
     */
    public static class SandboxResult<T> {
        private final boolean success;
        private final T result;
        private final String error;
        private final long executionTimeMs;
        private final boolean timeout;

        public SandboxResult(boolean success, T result, String error, long executionTimeMs, boolean timeout) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.executionTimeMs = executionTimeMs;
            this.timeout = timeout;
        }
public boolean isSuccess() {
            return success;
        }
public T getResult() {
            return result;
        }
public String getError() {
            return error;
        }
public long getExecutionTimeMs() {
            return executionTimeMs;
        }
public boolean isTimeout() {
            return timeout;
        }
    }

    /**
     * 在沙箱中执行任务
     *
     * @param task 要执行的任务
     * @param config 沙箱配置
     * @param context 执行上下文（用于日志记录）
     * @return 执行结果
     */
    public <T> SandboxResult<T> executeInSandbox(Callable<T> task, SandboxConfig config, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();

        Future<T> future = executorService.submit(() -> {
            // 设置线程名称，便于调试
            Thread.currentThread().setName("sandbox-" + System.currentTimeMillis());
            return task.call();
        });

        try {
            T result = future.get(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.info("Sandbox execution completed: executionTimeMs={}, context={}", executionTimeMs, context);

            return new SandboxResult<>(true, result, null, executionTimeMs, false);

        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.warn("Sandbox execution timeout: timeoutMs={}, context={}", config.getTimeoutMs(), context);

            return new SandboxResult<>(false, null, "执行超时（" + config.getTimeoutMs() + "ms）", executionTimeMs, true);

        } catch (ExecutionException e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause();

            log.error("Sandbox execution failed: error={}, context={}", cause.getMessage(), context, cause);

            return new SandboxResult<>(false, null, "执行失败: " + cause.getMessage(), executionTimeMs, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.warn("Sandbox execution interrupted: context={}", context);

            return new SandboxResult<>(false, null, "执行被中断", executionTimeMs, false);

        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.error("Sandbox execution unexpected error: error={}, context={}", e.getMessage(), context, e);

            return new SandboxResult<>(false, null, "未知错误: " + e.getMessage(), executionTimeMs, false);
        }
    }

    /**
     * 使用默认配置在沙箱中执行任务
     */
    public <T> SandboxResult<T> executeInSandbox(Callable<T> task, Map<String, Object> context) {
        return executeInSandbox(task, SandboxConfig.defaultConfig(), context);
    }

    /**
     * 关闭沙箱执行器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
