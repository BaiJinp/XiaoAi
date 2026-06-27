package com.xiaoai.agent.memory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.memory.entity.AgentMemory;
import com.xiaoai.agent.memory.mapper.AgentMemoryMapper;
import com.xiaoai.agent.memory.model.AgentMemoryPageQuery;
import com.xiaoai.agent.memory.model.CreateAgentMemoryCommand;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class AgentMemoryServiceImpl extends ServiceImpl<AgentMemoryMapper, AgentMemory> implements AgentMemoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
public AgentMemory createConfirmedMemory(CreateAgentMemoryCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        Long userId = UserContextHolder.requireUserId();
        if (!StringUtils.hasText(command.getSummaryText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Memory summary is required");
        }
        AgentMemory memory = new AgentMemory();
        memory.setTenantId(tenantId);
        memory.setMemoryCode("MEM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        memory.setAgentId(command.getAgentId());
        memory.setAgentVersionId(command.getAgentVersionId());
        memory.setTaskId(command.getTaskId());
        memory.setRunId(command.getRunId());
        memory.setSessionId(command.getSessionId());
        memory.setUserId(command.getUserId() == null ? userId : command.getUserId());
        memory.setMemoryType(defaultText(command.getMemoryType(), "session_summary"));
        memory.setMemoryScope(defaultText(command.getMemoryScope(), inferScope(command)));
        memory.setSummaryText(command.getSummaryText().trim());
        memory.setSourceText(command.getSourceText());
        memory.setConfidence(defaultText(command.getConfidence(), "confirmed"));
        memory.setStatus("confirmed");
        memory.setPolicyJson(defaultText(command.getPolicyJson(), "{\"write\":\"confirmed_only\"}"));
        save(memory);
        return memory;
    }

    @Override
public PageResponse<AgentMemory> pageMemories(AgentMemoryPageQuery query) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(query.getAgentId() != null, AgentMemory::getAgentId, query.getAgentId())
                .eq(query.getTaskId() != null, AgentMemory::getTaskId, query.getTaskId())
                .eq(query.getSessionId() != null, AgentMemory::getSessionId, query.getSessionId())
                .eq(query.getUserId() != null, AgentMemory::getUserId, query.getUserId())
                .eq(AgentMemory::getStatus, defaultText(query.getStatus(), "confirmed"))
                .orderByDesc(AgentMemory::getCreatedAt);
        Page<AgentMemory> page = page(new Page<>(query.normalizedPageNo(), query.normalizedPageSize()), wrapper);
        return PageResponse.<AgentMemory>builder()
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(page.getRecords())
                .build();
    }

    @Override
public List<AgentMemory> listConfirmedMemoriesForRuntime(Long tenantId,
                                                             Long agentId,
                                                             Long taskId,
                                                             Long sessionId,
                                                             Long userId,
                                                             int limit) {
        return listConfirmedMemoriesForRuntime(tenantId, agentId, taskId, sessionId, userId, List.of("task"), limit);
    }

    @Override
public List<AgentMemory> listConfirmedMemoriesForRuntime(Long tenantId,
                                                             Long agentId,
                                                             Long taskId,
                                                             Long sessionId,
                                                             Long userId,
                                                             List<String> scopes,
                                                             int limit) {
        if (tenantId == null) {
            return List.of();
        }
        List<String> normalizedScopes = normalizeScopes(scopes);
        boolean includeTask = normalizedScopes.contains("task") && taskId != null;
        boolean includeSession = normalizedScopes.contains("session") && sessionId != null;
        boolean includeAgent = normalizedScopes.contains("agent") && agentId != null;
        if (normalizedScopes.isEmpty() || (!includeTask && !includeSession && !includeAgent)) {
            return List.of();
        }
        LambdaQueryWrapper<AgentMemory> wrapper = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getStatus, "confirmed")
                .eq(agentId != null, AgentMemory::getAgentId, agentId)
                .eq(userId != null, AgentMemory::getUserId, userId)
                .and(scopeWrapper -> {
                    boolean[] hasScope = {false};
                    if (includeTask) {
                        scopeWrapper.eq(AgentMemory::getMemoryScope, "task")
                                .eq(AgentMemory::getTaskId, taskId);
                        hasScope[0] = true;
                    }
                    if (includeSession) {
                        if (hasScope[0]) {
                            scopeWrapper.or();
                        }
                        scopeWrapper.eq(AgentMemory::getMemoryScope, "session")
                                .eq(AgentMemory::getSessionId, sessionId);
                        hasScope[0] = true;
                    }
                    if (includeAgent) {
                        if (hasScope[0]) {
                            scopeWrapper.or();
                        }
                        scopeWrapper.eq(AgentMemory::getMemoryScope, "agent")
                                .eq(AgentMemory::getAgentId, agentId);
                    }
                })
                .orderByDesc(AgentMemory::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 20)));
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public AgentMemory archiveMemory(Long memoryId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentMemory memory = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getTenantId, tenantId)
                .eq(AgentMemory::getId, memoryId)
                .last("limit 1"));
        if (memory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent memory not found");
        }
        memory.setStatus("archived");
        updateById(memory);
        return memory;
    }

    private String inferScope(CreateAgentMemoryCommand command) {
        if (command.getTaskId() != null) {
            return "task";
        }
        if (command.getSessionId() != null) {
            return "session";
        }
        return "agent";
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of("task");
        }
        return scopes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(scope -> "task".equals(scope) || "session".equals(scope) || "agent".equals(scope))
                .distinct()
                .toList();
    }
}
