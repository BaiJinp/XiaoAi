package com.xiaoai.agent.terminal;

/**
 * 终端后端接口
 * 支持在不同的执行环境中运行命令
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface TerminalBackend {

    /**
     * 获取后端类型
     */
    String getBackendType();

    /**
     * 执行命令
     */
    TerminalResult execute(String command, TerminalConfig config);

    /**
     * 检查后端是否可用
     */
    boolean isAvailable();

    /**
     * 获取后端状态
     */
    BackendStatus getStatus();

    /**
     * 后端状态
     */
    enum BackendStatus {
        AVAILABLE,
        UNAVAILABLE,
        ERROR
    }

    /**
     * 终端结果
     */
    class TerminalResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final long executionTimeMs;

        public TerminalResult(int exitCode, String stdout, String stderr, long executionTimeMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.executionTimeMs = executionTimeMs;
        }
public int getExitCode() { return exitCode; }
public String getStdout() { return stdout; }
public String getStderr() { return stderr; }
public long getExecutionTimeMs() { return executionTimeMs; }
public boolean isSuccess() { return exitCode == 0; }
    }

    /**
     * 终端配置
     */
    class TerminalConfig {
        private String workingDirectory;
        private Map<String, String> environment;
        private long timeoutMs;
        private String user;
        private String password;

        // Getters and setters
public String getWorkingDirectory() { return workingDirectory; }
public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
public Map<String, String> getEnvironment() { return environment; }
public void setEnvironment(Map<String, String> environment) { this.environment = environment; }
public long getTimeoutMs() { return timeoutMs; }
public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
public String getUser() { return user; }
public void setUser(String user) { this.user = user; }
public String getPassword() { return password; }
public void setPassword(String password) { this.password = password; }
    }
}
