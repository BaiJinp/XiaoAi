package com.xiaoai.agent.terminal.impl;

import com.xiaoai.agent.terminal.TerminalBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Docker 终端后端
 * 在 Docker 容器中执行命令
 */
@Component
public class DockerTerminalBackend implements TerminalBackend {

    private static final Logger log = LoggerFactory.getLogger(DockerTerminalBackend.class);

    @Override
public String getBackendType() {
        return "docker";
    }

    @Override
public TerminalResult execute(String command, TerminalConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            // 构建 docker exec 命令
            String containerId = config.getEnvironment().get("DOCKER_CONTAINER_ID");
            if (containerId == null || containerId.isEmpty()) {
                return new TerminalResult(-1, "", "DOCKER_CONTAINER_ID not specified",
                        System.currentTimeMillis() - startTime);
            }

            // 构建完整的 docker 命令
            String dockerCommand = String.format("docker exec %s bash -c \"%s\"",
                    containerId, command.replace("\"", "\\\""));

            // 使用本地后端执行 docker 命令
            LocalTerminalBackend localBackend = new LocalTerminalBackend();
            TerminalResult result = localBackend.execute(dockerCommand, config);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Docker command executed: container={}, exitCode={}, time={}ms",
                    containerId, result.getExitCode(), executionTime);

            return result;

        } catch (Exception e) {
            log.error("Error executing docker command", e);
            return new TerminalResult(-1, "", e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
public boolean isAvailable() {
        try {
            // 检查 docker 是否可用
            LocalTerminalBackend localBackend = new LocalTerminalBackend();
            TerminalResult result = localBackend.execute("docker --version", new TerminalConfig());
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
