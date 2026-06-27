package com.xiaoai.agent.subagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.subagent.entity.SubAgent;
import com.xiaoai.agent.subagent.mapper.SubAgentMapper;
import com.xiaoai.agent.subagent.service.SubAgentService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 子代理服务实现
 */
@Service
public class SubAgentServiceImpl extends ServiceImpl<SubAgentMapper, SubAgent>
        implements SubAgentService {

    @Override
public SubAgent createSubAgent(Long tenantId, Long parentTaskId, Long parentRunId,
                                    Long agentId, Long agentVersionId, String taskDescription,
                                    Integer priority, Boolean isolated, Long timeoutMs) {
        SubAgent subAgent = new SubAgent();
        subAgent.setTenantId(tenantId);
        subAgent.setSubAgentCode("subagent_" + UUID.randomUUID().toString().substring(0, 8));
        subAgent.setSubAgentName("SubAgent-" + subAgent.getSubAgentCode());
        subAgent.setParentTaskId(parentTaskId);
        subAgent.setParentRunId(parentRunId);
        subAgent.setAgentId(agentId);
        subAgent.setAgentVersionId(agentVersionId);
        subAgent.setTaskDescription(taskDescription);
        subAgent.setStatus("pending");
        subAgent.setPriority(priority != null ? priority : 10);
        subAgent.setIsolated(isolated != null ? isolated : true);
        subAgent.setTimeoutMs(timeoutMs != null ? timeoutMs : 300000L); // 默认5分钟
        subAgent.setCreatedAt(OffsetDateTime.now());
        subAgent.setUpdatedAt(OffsetDateTime.now());

        save(subAgent);
        return subAgent;
    }

    @Override
public List<SubAgent> getSubAgentsByParent(Long parentTaskId) {
        return list(new LambdaQueryWrapper<SubAgent>()
                .eq(SubAgent::getParentTaskId, parentTaskId)
                .orderByAsc(SubAgent::getPriority));
    }

    @Override
public void updateStatus(Long subAgentId, String status) {
        SubAgent subAgent = getById(subAgentId);
        if (subAgent != null) {
            subAgent.setStatus(status);
            if ("running".equals(status)) {
                subAgent.setStartTime(OffsetDateTime.now());
            } else if ("completed".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                subAgent.setEndTime(OffsetDateTime.now());
            }
            subAgent.setUpdatedAt(OffsetDateTime.now());
            updateById(subAgent);
        }
    }

    @Override
public void setResult(Long subAgentId, String result, Integer tokenUsage) {
        SubAgent subAgent = getById(subAgentId);
        if (subAgent != null) {
            subAgent.setResult(result);
            subAgent.setTokenUsage(tokenUsage);
            subAgent.setStatus("completed");
            subAgent.setEndTime(OffsetDateTime.now());
            subAgent.setUpdatedAt(OffsetDateTime.now());
            updateById(subAgent);
        }
    }

    @Override
public void setError(Long subAgentId, String errorMessage) {
        SubAgent subAgent = getById(subAgentId);
        if (subAgent != null) {
            subAgent.setErrorMessage(errorMessage);
            subAgent.setStatus("failed");
            subAgent.setEndTime(OffsetDateTime.now());
            subAgent.setUpdatedAt(OffsetDateTime.now());
            updateById(subAgent);
        }
    }

    @Override
public void cancelSubAgent(Long subAgentId) {
        SubAgent subAgent = getById(subAgentId);
        if (subAgent != null) {
            subAgent.setStatus("cancelled");
            subAgent.setEndTime(OffsetDateTime.now());
            subAgent.setUpdatedAt(OffsetDateTime.now());
            updateById(subAgent);
        }
    }

    @Override
public List<SubAgent> getPendingSubAgents() {
        return list(new LambdaQueryWrapper<SubAgent>()
                .eq(SubAgent::getStatus, "pending")
                .orderByAsc(SubAgent::getPriority));
    }

    @Override
public List<SubAgent> getRunningSubAgents() {
        return list(new LambdaQueryWrapper<SubAgent>()
                .eq(SubAgent::getStatus, "running"));
    }
}
