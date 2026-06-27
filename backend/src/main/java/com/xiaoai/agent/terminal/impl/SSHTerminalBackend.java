package com.xiaoai.agent.terminal.impl;

import com.xiaoai.agent.terminal.TerminalBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SSH 终端后端
 * 通过 SSH 在远程服务器执行命令
 */
@Component
public class SSHTerminalBackend implements TerminalBackend {

    private static final Logger log = LoggerFactory.getLogger(SSHTerminalBackend.class);

    @Override
public String getBackendType() {
        return "ssh";
    }

    @Override
public TerminalResult execute(String command, TerminalConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            // 获取 SSH 配置
            String host = config.getEnvironment().get("SSH_HOST");
            String user = config.getUser();
            String port = config.getEnvironment().getOrDefault("SSH_PORT", "22");

            if (host == null || host.isEmpty()) {
                return new TerminalResult(-1, "", "SSH_HOST not specified",
                        System.currentTimeMillis() - startTime);
            }

            if (user == null || user.isEmpty()) {
                return new TerminalResult(-1, "", "SSH user not specified",
                        System.currentTimeMillis() - startTime);
            }

            // 构建 ssh 命令
            String sshCommand = String.format("ssh -o StrictHostKeyChecking=no -p %s %s@%s \"%s\"",
                    port, user, host, command.replace("\"", "\\\""));

            // 使用本地后端执行 ssh 命令
            LocalTerminalBackend localBackend = new LocalTerminalBackend();
            TerminalResult result = localBackend.execute(sshCommand, config);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("SSH command executed: host={}, user={}, exitCode={}, time={}ms",
                    host, user, result.getExitCode(), executionTime);

            return result;

        } catch (Exception e) {
            log.error("Error executing SSH command", e);
            return new TerminalResult(-1, "", e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
public boolean isAvailable() {
        try {
            // 检查 ssh 是否可用
            LocalTerminalBackend localBackend = new LocalTerminalBackend();
            TerminalResult result = localBackend.execute("ssh -V", new TerminalConfig());
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
public BackendStatus getStatus() {
        return isAvailable() ? BackendStatus.AVAILABLE : BackendStatus.UNAVAILABLE;
    }
}
