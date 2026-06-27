package com.xiaoai.agent.terminal.impl;

import com.xiaoai.agent.terminal.TerminalBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 本地终端后端
 * 在本地文件系统执行命令
 */
@Component
public class LocalTerminalBackend implements TerminalBackend {

    private static final Logger log = LoggerFactory.getLogger(LocalTerminalBackend.class);

    @Override
public String getBackendType() {
        return "local";
    }

    @Override
public TerminalResult execute(String command, TerminalConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();

            // 根据操作系统设置命令
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }

            // 设置工作目录
            if (config.getWorkingDirectory() != null) {
                processBuilder.directory(new java.io.File(config.getWorkingDirectory()));
            }

            // 设置环境变量
            if (config.getEnvironment() != null) {
                processBuilder.environment().putAll(config.getEnvironment());
            }

            // 启动进程
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.error("Error reading stdout", e);
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.error("Error reading stderr", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            // 等待进程完成
            long timeout = config.getTimeoutMs() > 0 ? config.getTimeoutMs() : 60000;
            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);

            if (!completed) {
                process.destroyForcibly();
                return new TerminalResult(-1, stdout.toString(), "Command timed out",
                        System.currentTimeMillis() - startTime);
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Local command executed: exitCode={}, time={}ms", exitCode, executionTime);

            return new TerminalResult(exitCode, stdout.toString(), stderr.toString(), executionTime);

        } catch (Exception e) {
            log.error("Error executing local command", e);
            return new TerminalResult(-1, "", e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
public boolean isAvailable() {
        return true; // 本地后端总是可用的
    }

    @Override
public BackendStatus getStatus() {
        return BackendStatus.AVAILABLE;
    }
}
