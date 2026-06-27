package com.xiaoai.agent.terminal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 终端后端注册表
 * 管理和选择不同的终端后端
 */
@Service
public class TerminalBackendRegistry {

    private final Map<String, TerminalBackend> backends;

    @Autowired
    public TerminalBackendRegistry(List<TerminalBackend> backendList) {
        this.backends = backendList.stream()
                .collect(Collectors.toMap(TerminalBackend::getBackendType, b -> b));
    }

    /**
     * 获取指定类型的后端
     */
    public TerminalBackend getBackend(String backendType) {
        TerminalBackend backend = backends.get(backendType);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown backend type: " + backendType);
        }
        return backend;
    }

    /**
     * 获取所有可用的后端
     */
    public List<TerminalBackend> getAvailableBackends() {
        return backends.values().stream()
                .filter(TerminalBackend::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有后端类型
     */
    public List<String> getBackendTypes() {
        return List.copyOf(backends.keySet());
    }

    /**
     * 检查后端是否可用
     */
    public boolean isBackendAvailable(String backendType) {
        TerminalBackend backend = backends.get(backendType);
        return backend != null && backend.isAvailable();
    }

    /**
     * 获取后端状态
     */
    public TerminalBackend.BackendStatus getBackendStatus(String backendType) {
        TerminalBackend backend = backends.get(backendType);
        return backend != null ? backend.getStatus() : TerminalBackend.BackendStatus.UNAVAILABLE;
    }
}
