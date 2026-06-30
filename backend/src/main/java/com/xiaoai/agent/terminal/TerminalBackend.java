package com.xiaoai.agent.terminal;

import java.util.Map;

/**
 * 缁堢鍚庣鎺ュ彛
 * 鏀寔鍦ㄤ笉鍚岀殑鎵ц鐜涓繍琛屽懡浠? *
 * @author Agent-xiaoAI Team
 * @date 2026-06-26
 */
public interface TerminalBackend {

    /**
     * 鑾峰彇鍚庣绫诲瀷
     */
    String getBackendType();

    /**
     * 鎵ц鍛戒护
     */
    TerminalResult execute(String command, TerminalConfig config);

    /**
     * 妫€鏌ュ悗绔槸鍚﹀彲鐢?     */
    boolean isAvailable();

    /**
     * 鑾峰彇鍚庣鐘舵€?     */
    BackendStatus getStatus();

    /**
     * 鍚庣鐘舵€?     */
    enum BackendStatus {
        AVAILABLE,
        UNAVAILABLE,
        ERROR
    }

    /**
     * 缁堢缁撴灉
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
     * 缁堢閰嶇疆
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
